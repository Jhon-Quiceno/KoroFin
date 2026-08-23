package com.korofin.backend.debt.exception;

import com.korofin.backend.common.exception.ResourceNotFoundException;

/**
 * Se lanza cuando la deuda pedida no existe <b>o</b> pertenece a otro usuario. Los dos casos
 * comparten excepción y código HTTP a propósito: responder {@code 403} a una deuda ajena
 * confirmaría su existencia a quien no es su dueño, así que toda mutación sobre una deuda ajena
 * devuelve {@code 404}, igual que si no existiera.
 *
 * <p>Extiende {@link ResourceNotFoundException} para reusar su mapeo a {@code 404 Not Found} en
 * {@code GlobalExceptionHandler} sin duplicar un handler.
 */
public class DebtNotFoundException extends ResourceNotFoundException {

    private static final String DEFAULT_MESSAGE = "Deuda no encontrada";

    public DebtNotFoundException() {
        super(DEFAULT_MESSAGE);
    }
}
