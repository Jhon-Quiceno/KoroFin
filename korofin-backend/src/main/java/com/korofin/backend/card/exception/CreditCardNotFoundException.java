package com.korofin.backend.card.exception;

import com.korofin.backend.common.exception.ResourceNotFoundException;

/**
 * Se lanza cuando la tarjeta pedida no existe <b>o</b> pertenece a otro usuario. Mismo criterio
 * que {@code DebtNotFoundException}: una tarjeta ajena devuelve {@code 404} y nunca {@code 403},
 * para no filtrar su existencia a quien no es su dueño.
 *
 * <p>Extiende {@link ResourceNotFoundException} para reusar su mapeo a {@code 404 Not Found} en
 * {@code GlobalExceptionHandler} sin duplicar un handler.
 */
public class CreditCardNotFoundException extends ResourceNotFoundException {

    private static final String DEFAULT_MESSAGE = "Tarjeta no encontrada";

    public CreditCardNotFoundException() {
        super(DEFAULT_MESSAGE);
    }
}
