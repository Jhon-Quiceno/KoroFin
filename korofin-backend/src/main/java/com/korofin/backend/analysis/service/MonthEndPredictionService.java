package com.korofin.backend.analysis.service;

import com.korofin.backend.analysis.dto.MonthEndPredictionResponse;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.common.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Proyecta el gasto de fin de mes del usuario a partir de su ritmo de gasto observado hasta hoy:
 * gasto promedio diario del mes en curso, extrapolado a la cantidad total de días del mes.
 *
 * <p>Es una proyección simple de ritmo constante (no una regresión ni nada que aprenda de meses
 * anteriores) — a propósito, ver docs/backend-plan.md sección 2.12: el dominio {@code analysis}
 * prioriza cifras explicables sobre precisión estadística. {@code MonthEndPredictionJob} (dominio
 * {@code scheduling}) usa este servicio para disparar una notificación con la proyección.
 */
@Service
public class MonthEndPredictionService {

    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    public MonthEndPredictionService(ExpenseRepository expenseRepository, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthEndPredictionResponse predict() {
        return predict(SecurityUtils.getCurrentUserId());
    }

    /**
     * Igual que {@link #predict()} pero para un llamador que ya resolvió {@code userId} por su
     * cuenta (usado por {@code MonthEndPredictionJob}, que no corre dentro de una request
     * autenticada).
     */
    @Transactional(readOnly = true)
    public MonthEndPredictionResponse predict(Long userId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate start = currentMonth.atDay(1);

        BigDecimal currentExpense = nullToZero(expenseRepository.sumAmountByUserAndPeriod(userId, start, today));
        int daysElapsed = today.getDayOfMonth();
        int daysInMonth = currentMonth.lengthOfMonth();

        BigDecimal averageDailyExpense = daysElapsed == 0
                ? BigDecimal.ZERO
                : currentExpense.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
        BigDecimal projectedExpense = averageDailyExpense.multiply(BigDecimal.valueOf(daysInMonth));

        return new MonthEndPredictionResponse(
                currentMonth.getYear(), currentMonth.getMonthValue(),
                currentExpense, averageDailyExpense, projectedExpense,
                daysElapsed, daysInMonth
        );
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
