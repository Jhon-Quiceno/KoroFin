package com.korofin.backend.dto.analysis;

import java.math.BigDecimal;

/**
 * Proyección de gasto de fin de mes para el usuario actual, calculada por
 * {@code MonthEndPredictionService#predict} a partir del ritmo de gasto observado hasta hoy.
 *
 * @param periodYear         año calendario del mes en curso
 * @param periodMonth        mes calendario en curso (1-12)
 * @param currentExpense     total gastado hasta hoy en el mes en curso
 * @param averageDailyExpense {@code currentExpense / daysElapsed}, cero si hoy es el primer día del mes
 * @param projectedExpense   {@code averageDailyExpense * daysInMonth} — proyección simple de ritmo constante
 * @param daysElapsed        días transcurridos del mes, incluido hoy
 * @param daysInMonth        cantidad total de días del mes en curso
 */
public record MonthEndPredictionResponse(
        int periodYear,
        int periodMonth,
        BigDecimal currentExpense,
        BigDecimal averageDailyExpense,
        BigDecimal projectedExpense,
        int daysElapsed,
        int daysInMonth
) {
}
