package com.korofin.backend.dto.recurringpayment;

/**
 * Resultado de ejecutar un pago recurrente vía {@code PATCH /api/recurring/{id}/pay}.
 *
 * <p>Decisión de diseño: en vez de devolver solo el {@link RecurringPaymentResponse} actualizado,
 * este compuesto también lleva el identificador del
 * {@link com.korofin.backend.entity.expense.Expense} creado para esta ejecución, así quien llama
 * puede navegar al gasto generado sin una consulta extra.
 *
 * @param recurringPayment pago recurrente actualizado, con el {@code nextPaymentDate} recalculado
 * @param expenseId        identificador del gasto creado para esta ejecución del pago
 */
public record RecurringPaymentPayResponse(
        RecurringPaymentResponse recurringPayment,
        Long expenseId
) {
}
