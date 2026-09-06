package com.korofin.backend.notification.service.channel;

/**
 * Instantánea de valor plano del destinatario de una notificación push.
 *
 * <p>Espeja el razonamiento de {@link EmailRecipient}: las implementaciones de
 * {@link PushNotificationSender} (en particular {@link ExpoPushAdapter}, que entrega en un hilo
 * {@code @Async}) nunca deben recibir una entidad JPA como {@code PushToken} o {@code User} —
 * {@link NotificationDispatcher} resuelve estos valores planos antes de entregárselos al sender
 * async.
 *
 * @param userId        dueño, se guarda solo para logging/correlación
 * @param expoPushToken el token de Expo push al que entregar
 */
public record PushRecipient(Long userId, String expoPushToken) {
}
