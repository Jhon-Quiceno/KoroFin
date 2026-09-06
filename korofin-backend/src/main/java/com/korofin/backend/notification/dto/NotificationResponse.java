package com.korofin.backend.notification.dto;

import com.korofin.backend.notification.entity.NotificationType;

import java.time.Instant;

/**
 * Modelo de lectura de una {@link com.korofin.backend.notification.entity.Notification}.
 *
 * @param id        identificador de la notificación
 * @param type      categoría de la notificación
 * @param title     título corto, orientado al usuario
 * @param message   cuerpo completo del mensaje, orientado al usuario
 * @param read      si el usuario actual ya la marcó como leída
 * @param readAt    instante en que se marcó como leída, o {@code null} si sigue sin leer
 * @param createdAt instante en que se creó
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
}
