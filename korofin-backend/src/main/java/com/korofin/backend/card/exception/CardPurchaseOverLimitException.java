package com.korofin.backend.card.exception;

/**
 * Se lanza cuando una compra haría que el saldo de la tarjeta superara su cupo. Mapeada a
 * {@code 400 Bad Request}: el request está bien formado, pero viola una regla de negocio.
 *
 * <p>La lanza {@code CardMovementService#registerPurchase} cuando el {@code UPDATE} atómico
 * condicionado al cupo devuelve {@code 0} filas, sin haber persistido ningún movimiento.
 */
public class CardPurchaseOverLimitException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "La compra supera el cupo disponible de la tarjeta";

    public CardPurchaseOverLimitException() {
        super(DEFAULT_MESSAGE);
    }
}
