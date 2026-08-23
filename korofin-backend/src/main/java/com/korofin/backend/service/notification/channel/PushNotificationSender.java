package com.korofin.backend.service.notification.channel;

/**
 * Puerto para un canal capaz de entregar una notificación a un dispositivo móvil registrado — hoy
 * solo Expo push ({@link ExpoPushAdapter}).
 *
 * <p>Deliberadamente no comparte abstracción con {@link NotificationSender}: ese puerto está atado
 * a {@link EmailRecipient} e inyectado en {@link NotificationDispatcher} como su única
 * implementación, así que un segundo {@code @Component implements NotificationSender} volvería
 * ambigua esa dependencia ({@code NoUniqueBeanDefinitionException}). Un dispositivo también puede
 * tener cero, uno o varios tokens registrados para un usuario — a diferencia del email, que es un
 * atributo fijo único de {@code User} — así que {@link NotificationDispatcher} envía un push por
 * token en vez de resolver un único destinatario como hace con el email.
 */
public interface PushNotificationSender {

    /**
     * Envía {@code title}/{@code body} a {@code recipient} a través de este canal.
     *
     * <p>Las implementaciones deben degradar en silencio ante cualquier falla — un canal push
     * inalcanzable/mal configurado/expirado nunca debe romper la notificación in-app que ya se
     * creó.
     *
     * @param recipient destinatario de valor plano (sin estado perezoso)
     * @param title     título corto, orientado al usuario
     * @param body      cuerpo completo del mensaje, orientado al usuario
     */
    void send(PushRecipient recipient, String title, String body);
}
