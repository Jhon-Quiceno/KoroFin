package com.korofin.backend.recurringpayment.exception;

import com.korofin.backend.common.exception.GlobalExceptionHandler;

/**
 * Se lanza cuando {@code PATCH /api/recurring/{id}/pay} pierde la carrera por avanzar
 * {@code nextPaymentDate} — otra ejecución (reintento del cliente tras un timeout, doble clic, una
 * segunda pestaña abierta) ya procesó este pago primero. Mapeada a {@code HTTP 409} por
 * {@link GlobalExceptionHandler} para que quien llama pueda distinguir "ya manejado por una
 * request concurrente" de una falla genérica y evitar reintentar a ciegas.
 */
public class RecurringPaymentAlreadyPaidException extends RuntimeException {

    public RecurringPaymentAlreadyPaidException(String message) {
        super(message);
    }
}
