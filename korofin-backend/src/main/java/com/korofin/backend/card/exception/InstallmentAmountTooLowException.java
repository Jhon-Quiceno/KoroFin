package com.korofin.backend.card.exception;

/**
 * Se lanza cuando el monto de una compra diferida es tan bajo, en relación al número de cuotas
 * elegido, que el redondeo a 2 decimales produciría capital cero o negativo en alguna cuota (por
 * ejemplo $0,35 a 48 cuotas). Mapeada a {@code 400 Bad Request}.
 *
 * <p>{@code AmortizationService} la lanza <b>antes</b> de tocar el saldo o persistir nada, así
 * que la compra se rechaza con cero efectos secundarios.
 */
public class InstallmentAmountTooLowException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "El monto de la compra es demasiado bajo para la cantidad de cuotas seleccionada";

    public InstallmentAmountTooLowException() {
        super(DEFAULT_MESSAGE);
    }
}
