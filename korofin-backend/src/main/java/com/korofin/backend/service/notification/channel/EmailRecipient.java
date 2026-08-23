package com.korofin.backend.service.notification.channel;

/**
 * Instantánea de valor plano del destinatario de una notificación por email.
 *
 * <p>Existe para que las implementaciones de {@link NotificationSender} (en particular
 * {@link EmailNotificationSender}, que entrega en un hilo {@code @Async}) nunca reciban una
 * entidad JPA como {@code User}. Un proxy cargado perezosamente que cruzara ese límite async
 * lanzaría {@code LazyInitializationException} una vez que el contexto de persistencia/transacción
 * de origen ya se cerró — {@link NotificationDispatcher} resuelve estos valores planos con una
 * carga eager antes de entregárselos al sender async.
 *
 * @param userId dueño, se guarda solo para logging/correlación
 * @param email  dirección de email del destinatario
 * @param name   nombre visible del destinatario
 */
public record EmailRecipient(Long userId, String email, String name) {
}
