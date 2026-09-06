package com.korofin.backend.card.exception;

import com.korofin.backend.common.exception.ResourceNotFoundException;

/**
 * Se lanza al pedir las cuotas de un movimiento que no tiene plan asociado (una compra simple, un
 * pago, un movimiento inexistente) o cuyo plan pertenece a otra tarjeta.
 *
 * <p>Extiende {@link ResourceNotFoundException} para reusar su mapeo a {@code 404 Not Found} en
 * {@code GlobalExceptionHandler} sin duplicar un handler.
 */
public class InstallmentPlanNotFoundException extends ResourceNotFoundException {

    private static final String DEFAULT_MESSAGE = "Plan de cuotas no encontrado";

    public InstallmentPlanNotFoundException() {
        super(DEFAULT_MESSAGE);
    }
}
