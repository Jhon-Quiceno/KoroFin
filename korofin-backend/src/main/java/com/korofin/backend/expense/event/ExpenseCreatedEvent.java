package com.korofin.backend.expense.event;

/**
 * Publicado por {@code ExpenseService#createExpense} después de persistir un {@code Expense}
 * nuevo.
 *
 * <p>Consumido por {@code OverspendAlertListener} (paquete {@code service/scheduling}), que
 * dispara una notificación de sobregasto sin acoplar {@code ExpenseService} a esa lógica — por eso
 * este evento existe en vez de una llamada directa.
 *
 * <p>Carga deliberadamente solo ids, no el {@code Expense}/{@code ExpenseResponse} completo: el
 * listener recalcula el agregado actual desde la base de datos en vez de confiar en una
 * instantánea tomada al momento de publicar, ya que pueden haberse creado otros gastos
 * concurrentemente.
 *
 * @param userId    dueño del gasto creado
 * @param expenseId id del gasto creado
 */
public record ExpenseCreatedEvent(Long userId, Long expenseId) {
}
