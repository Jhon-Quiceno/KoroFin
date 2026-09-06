package com.korofin.backend.notification.dto;

/**
 * Modelo de lectura de las {@link com.korofin.backend.notification.entity.NotificationPreference}
 * del usuario actual.
 *
 * @param paymentReminders    si se entrega {@code PAYMENT_REMINDER}
 * @param overspendAlerts     si se entrega {@code OVERSPEND_ALERT}
 * @param weeklySummary       si se entrega {@code WEEKLY_SUMMARY}
 * @param inactivityReminders si se entrega {@code INACTIVITY_REMINDER}
 * @param cardCycleClose      si se entrega {@code CARD_CYCLE_CLOSE}
 * @param emailEnabled        si además se entrega por email todo tipo habilitado
 */
public record NotificationPreferenceResponse(
        boolean paymentReminders,
        boolean overspendAlerts,
        boolean weeklySummary,
        boolean inactivityReminders,
        boolean cardCycleClose,
        boolean emailEnabled
) {
}
