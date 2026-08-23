package com.korofin.backend.expense.entity;

/**
 * Método de pago usado para saldar un {@link Expense}.
 *
 * <p>Se persiste como columna {@code VARCHAR} con un {@code CHECK} constraint (ver
 * {@code V2__create_categories_expenses_incomes.sql}), siguiendo el mismo criterio simple
 * varchar-más-check del resto del proyecto para enumeraciones en vez de un tipo enum nativo de
 * PostgreSQL.
 */
public enum PaymentMethodType {
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    TRANSFER,
    OTHER
}
