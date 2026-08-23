package com.korofin.backend.income.repository;

import java.math.BigDecimal;

/**
 * Proyección del total de {@code Income} agrupado por categoría para un usuario/período, tal como
 * la devuelve {@link IncomeRepository#findTopCategoriesByUserAndPeriod}.
 *
 * <p>Espeja a {@code com.korofin.backend.expense.repository.CategoryTotalProjection}, pero se
 * mantiene como tipo separado para que {@code income} no dependa de {@code expense} solo por algo
 * tan básico como una proyección de totales por categoría.
 *
 * <p>{@link #getCategoryId()} y {@link #getCategoryName()} son {@code null} para ingresos sin
 * clasificar; quien consuma la proyección debe aplicar el fallback "Sin categoría" por su cuenta.
 */
public interface IncomeCategoryTotalProjection {

    Long getCategoryId();

    String getCategoryName();

    BigDecimal getTotal();
}
