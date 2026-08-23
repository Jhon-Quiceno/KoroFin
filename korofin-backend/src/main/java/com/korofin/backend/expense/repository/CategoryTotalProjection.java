package com.korofin.backend.expense.repository;

import java.math.BigDecimal;

/**
 * Proyección del total de {@code Expense} agrupado por categoría para un usuario/período, tal
 * como la devuelve {@link ExpenseRepository#findTopCategoriesByUserAndPeriod}.
 *
 * <p>{@link #getCategoryId()} y {@link #getCategoryName()} son {@code null} para gastos sin
 * clasificar; quien consuma la proyección debe aplicar el fallback "Sin categoría" por su cuenta
 * en vez de depender de la consulta, para que ese fallback siga siendo testeable sin base de
 * datos real.
 */
public interface CategoryTotalProjection {

    Long getCategoryId();

    String getCategoryName();

    BigDecimal getTotal();
}
