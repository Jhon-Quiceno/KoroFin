package com.korofin.backend.notification.service.channel;

/**
 * Puerto para un canal capaz de entregar una notificación fuera de la lista in-app — hoy solo
 * email ({@link EmailNotificationSender}), pero la abstracción existe para que
 * {@link NotificationDispatcher} no dependa de un transporte concreto.
 */
public interface NotificationSender {

    /**
     * Envía {@code subject}/{@code body} a {@code recipient} a través de este canal.
     *
     * <p>Recibe un {@link EmailRecipient} en vez de la entidad {@code User} porque las
     * implementaciones (ver {@link EmailNotificationSender}) pueden correr en un hilo
     * {@code @Async}, donde una entidad cargada perezosamente ya no tendría un contexto de
     * persistencia abierto contra el cual resolverse.
     *
     * <p>Las implementaciones deben degradar en silencio (loguear y retornar) cuando el canal no
     * está configurado, nunca lanzar hacia quien llama — un canal externo caído/faltante nunca
     * debe romper la notificación in-app que ya se creó.
     *
     * @param recipient destinatario de valor plano (sin estado perezoso)
     * @param subject   asunto/título corto, orientado al usuario
     * @param body      cuerpo completo del mensaje, orientado al usuario
     */
    void send(EmailRecipient recipient, String subject, String body);
}
