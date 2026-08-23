package com.korofin.backend.service.analysis;

import com.korofin.backend.dto.analysis.CategoryTotalResponse;
import com.korofin.backend.dto.analysis.FinancialSummaryResponse;
import com.korofin.backend.dto.analysis.MonthlyTotalResponse;
import com.korofin.backend.dto.analysis.RecommendationResponse;
import com.korofin.backend.entity.analysis.FinancialAnalysis;
import com.korofin.backend.repository.analysis.FinancialAnalysisRepository;
import com.korofin.backend.repository.common.MonthlyTotalProjection;
import com.korofin.backend.repository.expense.CategoryTotalProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuente de verdad de las cifras financieras agregadas del usuario: resumen mensual
 * (ingresos/gastos/ahorro/ratios), top categorías de gasto, serie mensual, y recomendaciones en
 * texto libre.
 *
 * <p>{@code ReportService} (dominio {@code report}) delega íntegramente en
 * {@link #getSummary(Long, YearMonth)} para su reporte mensual — <b>nunca recalcula nada</b>, solo
 * reordena esta misma forma de respuesta (ver docs/backend-plan.md sección 7).
 *
 * <p>Cada llamada a {@link #getSummary(Long, YearMonth)} recalcula las cifras desde
 * {@code Expense}/{@code Income} (nunca lee {@link FinancialAnalysis} para responder) y después
 * hace upsert de un snapshot {@link FinancialAnalysis} para ese {@code (usuario, período)}, dejando
 * un historial consultable sin volver a agregar movimientos para un período ya calculado.
 */
@Service
public class FinancialAnalysisService {

    /** Cantidad de meses (incluido el período pedido) que cubre {@link MonthlyTotalResponse} serie mensual. */
    private static final int SERIES_MONTHS = 6;

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final FinancialAnalysisRepository financialAnalysisRepository;
    private final UserRepository userRepository;

    public FinancialAnalysisService(
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            FinancialAnalysisRepository financialAnalysisRepository,
            UserRepository userRepository
    ) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.financialAnalysisRepository = financialAnalysisRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FinancialSummaryResponse getSummary(YearMonth period) {
        return getSummary(SecurityUtils.getCurrentUserId(), period);
    }

    /**
     * Igual que {@link #getSummary(YearMonth)} pero para un llamador que ya resolvió {@code userId}
     * por su cuenta (usado por {@code ReportService} y {@code MonthEndPredictionJob}, ninguno de los
     * cuales corre dentro de una request autenticada del propio usuario).
     */
    @Transactional
    public FinancialSummaryResponse getSummary(Long userId, YearMonth period) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        BigDecimal totalIncome = nullToZero(incomeRepository.sumAmountByUserAndPeriod(userId, start, end));
        BigDecimal totalExpense = nullToZero(expenseRepository.sumAmountByUserAndPeriod(userId, start, end));
        BigDecimal totalSavings = totalIncome.subtract(totalExpense);
        BigDecimal savingsRate = computeSavingsRate(totalIncome, totalSavings);

        List<CategoryTotalResponse> topExpenseCategories = expenseRepository
                .findTopCategoriesByUserAndPeriod(userId, start, end)
                .stream()
                .map(FinancialAnalysisService::toCategoryTotalResponse)
                .toList();

        List<MonthlyTotalResponse> monthlySeries = buildMonthlySeries(userId, period);

        persistSnapshot(userId, period, totalIncome, totalExpense, totalSavings, savingsRate);

        return new FinancialSummaryResponse(
                period.getYear(), period.getMonthValue(),
                totalIncome, totalExpense, totalSavings, savingsRate,
                topExpenseCategories, monthlySeries
        );
    }

    /**
     * Recomendaciones en texto libre para el usuario actual, derivadas del resumen del mes en
     * curso mediante heurísticas simples (sin IA, ver Javadoc de {@link RecommendationResponse}):
     * déficit/ahorro bajo, y concentración de gasto en una sola categoría.
     */
    @Transactional
    public List<RecommendationResponse> getRecommendations() {
        Long userId = SecurityUtils.getCurrentUserId();
        FinancialSummaryResponse summary = getSummary(userId, YearMonth.now());
        List<RecommendationResponse> recommendations = new ArrayList<>();

        if (summary.totalSavings().signum() < 0) {
            recommendations.add(new RecommendationResponse(
                    "Estás gastando más de lo que ingresa",
                    "Este mes tus gastos superaron tus ingresos por $" + summary.totalSavings().abs()
                            + ". Revisá tus categorías de mayor gasto para encontrar dónde ajustar."
            ));
        } else if (summary.savingsRate().compareTo(BigDecimal.TEN) < 0) {
            recommendations.add(new RecommendationResponse(
                    "Tu ahorro este mes es bajo",
                    "Estás ahorrando el " + summary.savingsRate() + "% de tus ingresos. Intentá apartar al menos "
                            + "un 10-20% antes de gastar el resto."
            ));
        } else {
            recommendations.add(new RecommendationResponse(
                    "Vas bien con tu ahorro",
                    "Este mes ahorraste el " + summary.savingsRate() + "% de tus ingresos. ¡Seguí así!"
            ));
        }

        if (!summary.topExpenseCategories().isEmpty() && summary.totalExpense().signum() > 0) {
            CategoryTotalResponse top = summary.topExpenseCategories().get(0);
            BigDecimal share = top.total()
                    .divide(summary.totalExpense(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (share.compareTo(BigDecimal.valueOf(40)) >= 0) {
                String categoryLabel = top.categoryName() == null ? "Sin categoría" : top.categoryName();
                recommendations.add(new RecommendationResponse(
                        "Un gasto concentrado en " + categoryLabel,
                        "El " + share.setScale(0, RoundingMode.HALF_UP) + "% de tu gasto de este mes está en "
                                + categoryLabel + ". Vale la pena revisar si ese ritmo es sostenible."
                ));
            }
        }

        return recommendations;
    }

    /** Serie de los últimos {@link #SERIES_MONTHS} meses (incluido {@code period}), sin huecos. */
    private List<MonthlyTotalResponse> buildMonthlySeries(Long userId, YearMonth period) {
        YearMonth seriesStart = period.minusMonths(SERIES_MONTHS - 1L);
        LocalDate start = seriesStart.atDay(1);
        LocalDate end = period.atEndOfMonth();

        Map<YearMonth, BigDecimal> incomeByMonth = toMonthMap(
                incomeRepository.sumAmountByUserGroupedByMonth(userId, start, end)
        );
        Map<YearMonth, BigDecimal> expenseByMonth = toMonthMap(
                expenseRepository.sumAmountByUserGroupedByMonth(userId, start, end)
        );

        List<MonthlyTotalResponse> series = new ArrayList<>(SERIES_MONTHS);
        for (int i = 0; i < SERIES_MONTHS; i++) {
            YearMonth month = seriesStart.plusMonths(i);
            series.add(new MonthlyTotalResponse(
                    month.getYear(), month.getMonthValue(),
                    incomeByMonth.getOrDefault(month, BigDecimal.ZERO),
                    expenseByMonth.getOrDefault(month, BigDecimal.ZERO)
            ));
        }
        return series;
    }

    private static Map<YearMonth, BigDecimal> toMonthMap(List<MonthlyTotalProjection> projections) {
        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        for (MonthlyTotalProjection projection : projections) {
            byMonth.put(YearMonth.of(projection.getPeriodYear(), projection.getPeriodMonth()), projection.getTotal());
        }
        return byMonth;
    }

    private static CategoryTotalResponse toCategoryTotalResponse(CategoryTotalProjection projection) {
        String categoryName = projection.getCategoryName() == null ? "Sin categoría" : projection.getCategoryName();
        return new CategoryTotalResponse(projection.getCategoryId(), categoryName, projection.getTotal());
    }

    private static BigDecimal computeSavingsRate(BigDecimal totalIncome, BigDecimal totalSavings) {
        if (totalIncome.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalSavings
                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void persistSnapshot(
            Long userId, YearMonth period,
            BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal totalSavings, BigDecimal savingsRate
    ) {
        FinancialAnalysis snapshot = financialAnalysisRepository
                .findByUser_IdAndPeriodYearAndPeriodMonth(userId, period.getYear(), period.getMonthValue())
                .orElseGet(() -> {
                    FinancialAnalysis created = new FinancialAnalysis();
                    created.setUser(userRepository.getReferenceById(userId));
                    created.setPeriodYear(period.getYear());
                    created.setPeriodMonth(period.getMonthValue());
                    return created;
                });

        snapshot.setTotalIncome(totalIncome);
        snapshot.setTotalExpense(totalExpense);
        snapshot.setTotalSavings(totalSavings);
        snapshot.setSavingsRate(savingsRate);
        financialAnalysisRepository.save(snapshot);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
