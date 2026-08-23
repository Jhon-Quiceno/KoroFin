package com.korofin.backend.entity.card;

/**
 * Tipo de asiento registrado contra una {@link CreditCard} como {@link CardMovement}.
 *
 * <p>{@link CreditCard#getCurrentBalance()} es siempre la suma con signo de todos los
 * movimientos: {@code PURCHASE}, {@code INSTALLMENT_PURCHASE}, {@code INTEREST} y {@code FEE} lo
 * incrementan; {@code PAYMENT} lo decrementa. El monto del movimiento siempre se guarda positivo
 * — es este tipo, y no el signo, el que determina el efecto sobre el saldo.
 *
 * <p>Se persiste como {@code VARCHAR} + {@code CHECK}, misma convención que
 * {@link CardFranchise}.
 */
public enum CardMovementType {
    PURCHASE,
    INSTALLMENT_PURCHASE,
    PAYMENT,
    INTEREST,
    FEE
}
