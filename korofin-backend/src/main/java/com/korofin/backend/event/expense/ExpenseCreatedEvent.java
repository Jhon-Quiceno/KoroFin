package com.korofin.backend.event.expense;

/**
 * Publicado por {@code ExpenseService#createExpense} después de persistir un {@code Expense}
 * nuevo.
 *
 * <p>Todavía no tiene listeners en esta fase — el dominio {@code notification}, que agregaría un
 * listener de alerta de sobregasto (equivalente a {@code OverspendAlertListener} en FinSmart, ver
 * {@code docs/backend-plan.md} sección 2.5), llega en una fase posterior. Publicar el evento desde
 * ya sin listeners es válido: {@code ExpenseService} queda desacoplado de esa lógica de
 * notificaciones sin tener que modificarse cuando esa fase lo agregue.
 *
 * <p>Carga deliberadamente solo ids, no el {@code Expense}/{@code ExpenseResponse} completo: un
 * futuro listener debe recalcular el agregado actual desde la base de datos en vez de confiar en
 * una instantánea tomada al momento de publicar, ya que pueden haberse creado otros gastos
 * concurrentemente.
 *
 * @param userId    dueño del gasto creado
 * @param expenseId id del gasto creado
 */
public record ExpenseCreatedEvent(Long userId, Long expenseId) {
}
