package com.korofin.backend.debt.exception;

/**
 * Se lanza cuando un abono supera el saldo restante de la deuda. Mapeada a
 * {@code 400 Bad Request}: el request está bien formado, pero viola una regla de negocio.
 *
 * <p>La lanzan dos puntos distintos de {@code DebtPaymentService#createPayment}: la verificación
 * rápida en memoria (mensaje más claro en el caso común) y el rechazo del {@code UPDATE} atómico
 * cuando otro abono concurrente ya consumió el saldo. Ambos casos son la misma regla vista desde
 * distinto momento, así que comparten excepción y mensaje.
 */
public class DebtPaymentExceedsBalanceException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "El abono no puede superar el saldo restante de la deuda";

    public DebtPaymentExceedsBalanceException() {
        super(DEFAULT_MESSAGE);
    }
}
