package com.korofin.backend.ai.service.provider;

import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.income.entity.Income;
import com.korofin.backend.common.repository.MonthlyTotalProjection;
import com.korofin.backend.debt.repository.DebtRepository;
import com.korofin.backend.expense.repository.CategoryTotalProjection;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.expense.repository.ExpenseSpecifications;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.income.repository.IncomeSpecifications;
import com.korofin.backend.common.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Arma el prompt de sistema en español que se inyecta en cada llamada de IA para el usuario
 * actual — esto es el "entrenamiento" de KoroFin: no hay fine-tuning, al modelo simplemente se le
 * da una foto compacta de los datos financieros reales del usuario en cada request (ver
 * {@code docs/backend-plan.md} sección 4).
 *
 * <p>A diferencia de FinSmart (que delegaba en {@code FinancialAnalysisService} del dominio
 * {@code analysis}), esta fase de KoroFin todavía no tiene ese dominio — se arma directamente
 * desde {@link ExpenseRepository}/{@link IncomeRepository}/{@link DebtRepository} (ya existentes
 * de fases anteriores), agregando el mes calendario actual en el momento en vez de delegar en un
 * servicio de resumen persistido. Cuando el dominio {@code analysis} exista, esta clase puede
 * simplificarse para delegar en él, igual que hacía FinSmart.
 */
@Component
public class FinancialContextBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TOP_CATEGORIES_LIMIT = 3;
    private static final int RECENT_TRANSACTIONS_LIMIT = 10;

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final DebtRepository debtRepository;
    private final Clock clock;

    public FinancialContextBuilder(
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            DebtRepository debtRepository,
            Clock clock
    ) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.debtRepository = debtRepository;
        this.clock = clock;
    }

    /**
     * Arma el prompt de sistema completo para el usuario autenticado actual (ver
     * {@link SecurityUtils#getCurrentUserId()}), cubriendo el mes calendario actual.
     *
     * @return el prompt de sistema en español ya armado, listo para enviarse como primer mensaje
     *         a {@link AiChatClient}
     */
    @Transactional(readOnly = true)
    public String buildSystemPrompt() {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();

        BigDecimal totalIncome = sumForMonth(incomeRepository.sumAmountByUserGroupedByMonth(userId, start, end), currentMonth);
        BigDecimal totalExpense = sumForMonth(expenseRepository.sumAmountByUserGroupedByMonth(userId, start, end), currentMonth);
        BigDecimal savings = totalIncome.subtract(totalExpense);
        BigDecimal expenseRatio = ratio(totalExpense, totalIncome);

        BigDecimal totalDebtRemaining = debtRepository.sumRemainingAmountByUser(userId);
        BigDecimal debtRatio = ratio(totalDebtRemaining, totalIncome);

        List<CategoryTotalProjection> topCategories = expenseRepository.findTopCategoriesByUserAndPeriod(userId, start, end);
        List<Debt> activeDebts = debtRepository.findAllByUser_Id(userId).stream()
                .filter(debt -> debt.getRemainingAmount() != null && debt.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        List<RecentTransaction> recentTransactions = buildRecentTransactions(userId);

        StringBuilder prompt = new StringBuilder();
        appendRulesBlock(prompt);
        appendSummaryBlock(prompt, currentMonth, totalIncome, totalExpense, savings, expenseRatio, debtRatio);
        appendTopCategoriesBlock(prompt, topCategories);
        appendRecentTransactionsBlock(prompt, recentTransactions);
        appendDebtsBlock(prompt, activeDebts);
        return prompt.toString();
    }

    private void appendRulesBlock(StringBuilder prompt) {
        prompt.append("Eres el asistente financiero personal de KoroFin para el usuario autenticado. Reglas:\n")
                .append("- Responde siempre en español.\n")
                .append("- Utiliza únicamente los datos financieros provistos a continuación; si falta un dato, "
                        + "indícalo explícitamente en lugar de inventarlo.\n")
                .append("- Expresa los montos en pesos colombianos (COP) con separador de miles "
                        + "(ejemplo: $1.234.567).\n")
                .append("- Tus respuestas son orientativas y no constituyen asesoría financiera regulada.\n")
                .append("- Responde únicamente preguntas relacionadas con la situación financiera personal del "
                        + "usuario (ingresos, gastos, deudas, ahorros, categorías o recomendaciones financieras) "
                        + "utilizando los datos provistos en este mensaje.\n")
                .append("- Si te preguntan sobre cualquier otro tema (deportes, política, entretenimiento, "
                        + "programación, cultura general o cualquier asunto no financiero), responde "
                        + "exclusivamente con este mensaje, sin agregar información adicional: "
                        + "\"Solo puedo ayudarte con preguntas sobre tu situación financiera registrada en "
                        + "KoroFin. ¿Querés que hablemos de tus gastos, ingresos, deudas o ahorros?\"\n\n");
    }

    private void appendSummaryBlock(
            StringBuilder prompt,
            YearMonth currentMonth,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal savings,
            BigDecimal expenseRatio,
            BigDecimal debtRatio
    ) {
        prompt.append(String.format(Locale.ROOT, "Resumen financiero del periodo %02d/%d:%n",
                        currentMonth.getMonthValue(), currentMonth.getYear()))
                .append("- Ingresos: $").append(formatCop(totalIncome)).append('\n')
                .append("- Gastos: $").append(formatCop(totalExpense)).append('\n')
                .append("- Balance (ahorro): $").append(formatCop(savings)).append('\n')
                .append("- Porcentaje de gasto sobre ingreso: ").append(formatPercentage(expenseRatio)).append('\n')
                .append("- Porcentaje de deuda sobre ingreso: ").append(formatPercentage(debtRatio)).append("\n\n");
    }

    private void appendTopCategoriesBlock(StringBuilder prompt, List<CategoryTotalProjection> topCategories) {
        prompt.append("Categorías con mayor gasto este periodo:\n");
        if (topCategories.isEmpty()) {
            prompt.append("- No hay datos de categorías para este periodo.\n\n");
            return;
        }
        topCategories.stream()
                .limit(TOP_CATEGORIES_LIMIT)
                .forEach(category -> prompt.append("- ")
                        .append(category.getCategoryName() != null ? category.getCategoryName() : "Sin categoría")
                        .append(": $").append(formatCop(category.getTotal())).append('\n'));
        prompt.append('\n');
    }

    private void appendRecentTransactionsBlock(StringBuilder prompt, List<RecentTransaction> transactions) {
        prompt.append("Movimientos recientes:\n");
        if (transactions.isEmpty()) {
            prompt.append("- No hay movimientos recientes registrados.\n\n");
            return;
        }
        transactions.forEach(transaction -> {
            String description = transaction.description() != null && !transaction.description().isBlank()
                    ? " | " + transaction.description()
                    : "";
            prompt.append("- ").append(transaction.date().format(DATE_FORMAT))
                    .append(" | ").append(transaction.typeLabel())
                    .append(" | $").append(formatCop(transaction.amount()))
                    .append(" | ").append(transaction.categoryName() != null ? transaction.categoryName() : "Sin categoría")
                    .append(description)
                    .append('\n');
        });
        prompt.append('\n');
    }

    private void appendDebtsBlock(StringBuilder prompt, List<Debt> activeDebts) {
        prompt.append("Deudas activas:\n");
        if (activeDebts.isEmpty()) {
            prompt.append("- El usuario no tiene deudas activas registradas.\n");
            return;
        }
        activeDebts.forEach(debt -> {
            String dueDate = debt.getDueDate() != null ? ", vence el " + debt.getDueDate().format(DATE_FORMAT) : "";
            prompt.append("- ").append(debt.getName())
                    .append(": saldo restante $").append(formatCop(debt.getRemainingAmount()))
                    .append(dueDate)
                    .append('\n');
        });
    }

    private List<RecentTransaction> buildRecentTransactions(Long userId) {
        // findAll(Specification, Pageable) en vez de un método de query nuevo en ExpenseRepository/
        // IncomeRepository: ambos repos ya extienden JpaSpecificationExecutor y ya exponen
        // ownedBy(userId) (ver ExpenseSpecifications/IncomeSpecifications), así que alcanza para
        // "los N movimientos más recientes de este usuario" sin tocar esas interfaces.
        var recentSort = Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "id"));
        List<RecentTransaction> expenses = expenseRepository
                .findAll(ExpenseSpecifications.ownedBy(userId), PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT, recentSort))
                .stream()
                .map(FinancialContextBuilder::fromExpense)
                .toList();
        List<RecentTransaction> incomes = incomeRepository
                .findAll(IncomeSpecifications.ownedBy(userId), PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT, recentSort))
                .stream()
                .map(FinancialContextBuilder::fromIncome)
                .toList();

        return java.util.stream.Stream.concat(expenses.stream(), incomes.stream())
                .sorted(Comparator.comparing(RecentTransaction::date).reversed())
                .limit(RECENT_TRANSACTIONS_LIMIT)
                .toList();
    }

    private static RecentTransaction fromExpense(Expense expense) {
        return new RecentTransaction(
                "Gasto",
                expense.getDate(),
                expense.getAmount(),
                expense.getCategory() != null ? expense.getCategory().getName() : null,
                expense.getDescription()
        );
    }

    private static RecentTransaction fromIncome(Income income) {
        return new RecentTransaction(
                "Ingreso",
                income.getDate(),
                income.getAmount(),
                income.getCategory() != null ? income.getCategory().getName() : null,
                income.getDescription()
        );
    }

    /** Suma el total del mes {@code month} en {@code rows}, o {@link BigDecimal#ZERO} si ese mes no tiene fila (sin movimientos). */
    private static BigDecimal sumForMonth(List<MonthlyTotalProjection> rows, YearMonth month) {
        return rows.stream()
                .filter(row -> row.getPeriodYear() == month.getYear() && row.getPeriodMonth() == month.getMonthValue())
                .map(MonthlyTotalProjection::getTotal)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /** {@code numerator / denominator}, o {@link BigDecimal#ZERO} cuando {@code denominator} es cero (evita dividir por cero). */
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    /**
     * Formatea {@code amount} como una cifra entera en pesos COP con separadores de miles
     * {@code .} (p. ej. {@code 1234567} -> {@code "1.234.567"}), usando un
     * {@link DecimalFormatSymbols} explícito en vez de un {@link Locale} {@code es-CO} para que la
     * salida no dependa de que la JVM tenga esos datos de locale disponibles.
     */
    private static String formatCop(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal rounded = safeAmount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        return format.format(rounded);
    }

    private static String formatPercentage(BigDecimal ratio) {
        BigDecimal safeRatio = ratio != null ? ratio : BigDecimal.ZERO;
        BigDecimal percentage = safeRatio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        return percentage + "%";
    }

    /** Fila normalizada para el bloque de movimientos recientes, unificando {@link Expense}/{@link Income}. */
    private record RecentTransaction(String typeLabel, LocalDate date, BigDecimal amount, String categoryName, String description) {
    }
}
