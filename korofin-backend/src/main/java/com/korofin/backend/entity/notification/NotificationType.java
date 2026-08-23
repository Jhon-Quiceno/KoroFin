package com.korofin.backend.entity.notification;

/**
 * Categoría de una {@link Notification}, usada tanto para renderizar un ícono/etiqueta en la UI
 * como para resolver el toggle correspondiente en {@link NotificationPreference} (ver
 * {@code NotificationDispatcher#isEnabledFor}).
 *
 * <p>{@link #MONTH_END_PREDICTION} y {@link #SYSTEM} no tienen toggle de preferencia propio —
 * siempre se entregan in-app cuando se crean. {@link #MONTH_END_PREDICTION} todavía no tiene
 * emisor: llega con {@code MonthEndPredictionJob} en la fase del dominio {@code analysis} (ver
 * el aviso en {@code docs/backend-plan.md} sección 2.10); el valor ya existe acá para no requerir
 * otra migración cuando esa fase lo agregue.
 */
public enum NotificationType {
    PAYMENT_REMINDER,
    OVERSPEND_ALERT,
    WEEKLY_SUMMARY,
    INACTIVITY_REMINDER,
    MONTH_END_PREDICTION,
    SYSTEM,
    CARD_CYCLE_CLOSE
}
