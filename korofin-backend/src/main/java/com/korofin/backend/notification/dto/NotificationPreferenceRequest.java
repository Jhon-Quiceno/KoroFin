package com.korofin.backend.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Payload para reemplazar las {@link com.korofin.backend.notification.entity.NotificationPreference}
 * del usuario actual.
 *
 * <p>Todos los campos son obligatorios: es un reemplazo completo ({@code PUT}), no un parche
 * parcial, así que quien llama debe enviar el conjunto completo de toggles cada vez (espeja el
 * formulario de preferencias de la app, que siempre renderiza todos los toggles).
 *
 * @param paymentReminders    si se entrega {@code PAYMENT_REMINDER}
 * @param overspendAlerts     si se entrega {@code OVERSPEND_ALERT}
 * @param weeklySummary       si se entrega {@code WEEKLY_SUMMARY}
 * @param inactivityReminders si se entrega {@code INACTIVITY_REMINDER}
 * @param cardCycleClose      si se entrega {@code CARD_CYCLE_CLOSE}
 * @param emailEnabled        si además se entrega por email todo tipo habilitado
 */
public record NotificationPreferenceRequest(
        @NotNull(message = "El campo paymentReminders es obligatorio")
        Boolean paymentReminders,
        @NotNull(message = "El campo overspendAlerts es obligatorio")
        Boolean overspendAlerts,
        @NotNull(message = "El campo weeklySummary es obligatorio")
        Boolean weeklySummary,
        @NotNull(message = "El campo inactivityReminders es obligatorio")
        Boolean inactivityReminders,
        @NotNull(message = "El campo cardCycleClose es obligatorio")
        Boolean cardCycleClose,
        @NotNull(message = "El campo emailEnabled es obligatorio")
        Boolean emailEnabled
) {
}
