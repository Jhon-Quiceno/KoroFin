package com.korofin.backend.repository.common;

import java.math.BigDecimal;

/**
 * Proyección de un total agrupado por mes calendario para un usuario, devuelta por
 * {@code ExpenseRepository#sumAmountByUserGroupedByMonth} y
 * {@code IncomeRepository#sumAmountByUserGroupedByMonth}.
 *
 * <p>Compartida entre los dominios {@code expense} e {@code income} (ver
 * {@code docs/backend-plan.md} sección 12) en vez de duplicada en cada uno, ya que la forma de la
 * proyección es idéntica en ambos casos. Pensada para alimentar el resumen financiero mensual de
 * una fase posterior ({@code analysis}): un mes sin filas simplemente está ausente del resultado,
 * quien consuma la proyección debe completar los huecos.
 */
public interface MonthlyTotalProjection {

    Integer getPeriodYear();

    Integer getPeriodMonth();

    BigDecimal getTotal();
}
