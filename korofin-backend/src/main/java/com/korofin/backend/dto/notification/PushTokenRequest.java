package com.korofin.backend.dto.notification;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload para registrar (o actualizar) un token de Expo push de uno de los dispositivos del
 * usuario actual.
 *
 * @param expoPushToken token que el SDK de Expo emitió para este dispositivo/instalación
 * @param deviceId      identificador estable del dispositivo, usado para hacer upsert en vez de
 *                      acumular filas duplicadas entre reinstalaciones/rotaciones de token — ver
 *                      {@code uk_push_tokens_user_device} en
 *                      {@code V5__create_notification_and_recurring_payment_domains.sql}
 */
public record PushTokenRequest(
        @NotBlank(message = "El campo expoPushToken es obligatorio")
        String expoPushToken,
        @NotBlank(message = "El campo deviceId es obligatorio")
        String deviceId
) {
}
