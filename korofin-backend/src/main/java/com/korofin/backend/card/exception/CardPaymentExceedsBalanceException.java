package com.korofin.backend.card.exception;

/**
 * Se lanza cuando un pago supera el saldo actual de la tarjeta. Mapeada a
 * {@code 400 Bad Request}: el request está bien formado, pero viola una regla de negocio.
 *
 * <p>La lanza {@code CardMovementService#registerPayment} cuando el {@code UPDATE} atómico
 * condicionado al saldo devuelve {@code 0} filas, sin haber persistido ningún movimiento.
 */
public class CardPaymentExceedsBalanceException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "El pago no puede superar el saldo actual de la tarjeta";

    public CardPaymentExceedsBalanceException() {
        super(DEFAULT_MESSAGE);
    }
}
