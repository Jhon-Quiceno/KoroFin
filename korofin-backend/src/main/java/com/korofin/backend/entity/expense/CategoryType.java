package com.korofin.backend.entity.expense;

/**
 * Clasifica una {@link Category} como categoría de ingreso o de gasto.
 *
 * <p>Se persiste como columna {@code VARCHAR} con un {@code CHECK} constraint (ver
 * {@code V2__create_categories_expenses_incomes.sql}), siguiendo el mismo criterio simple
 * varchar-más-check del resto del proyecto para enumeraciones en vez de un tipo enum nativo de
 * PostgreSQL.
 */
public enum CategoryType {
    EXPENSE,
    INCOME
}
