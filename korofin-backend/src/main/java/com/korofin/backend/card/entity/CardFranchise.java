package com.korofin.backend.card.entity;

/**
 * Franquicia (red de pagos) de una {@link CreditCard}.
 *
 * <p>Se persiste como columna {@code VARCHAR} con un {@code CHECK} en base de datos (ver
 * {@code V3__add_debt_and_card_domain.sql}), siguiendo la misma convención que el resto del
 * proyecto para enumeraciones (ver {@code com.korofin.backend.expense.entity.PaymentMethodType})
 * en vez de un tipo enum nativo de PostgreSQL.
 */
public enum CardFranchise {
    VISA,
    MASTERCARD,
    AMEX,
    DINERS
}
