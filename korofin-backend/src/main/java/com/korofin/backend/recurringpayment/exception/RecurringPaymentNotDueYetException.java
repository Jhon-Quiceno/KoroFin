package com.korofin.backend.recurringpayment.exception;

import com.korofin.backend.common.exception.GlobalExceptionHandler;

/**
 * Se lanza cuando se llama a {@code PATCH /api/recurring/{id}/pay} antes de que el
 * {@code nextPaymentDate} del pago recurrente realmente haya llegado. Sin este guard, hacer clic
 * repetidamente en "Marcar como pagado" (cada clic evaluado en un instante distinto, así que el
 * guard de carrera de {@link RecurringPaymentAlreadyPaidException} nunca se activaría) crearía un
 * {@code Expense} duplicado y avanzaría {@code nextPaymentDate} en cada clic. Mapeada a
 * {@code HTTP 409} por {@link GlobalExceptionHandler} para que quien llama pueda distinguir "todavía
 * no vence" de una falla genérica.
 */
public class RecurringPaymentNotDueYetException extends RuntimeException {

    public RecurringPaymentNotDueYetException(String message) {
        super(message);
    }
}
