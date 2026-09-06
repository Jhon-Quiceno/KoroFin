package com.korofin.backend.recurringpayment.entity;

/**
 * Ciclo de facturación de un {@link RecurringPayment}.
 *
 * <p>Persistido como columna {@code VARCHAR} con un {@code CHECK} de base de datos (ver
 * {@code V5__create_notification_and_recurring_payment_domains.sql}), siguiendo el mismo patrón
 * simple de varchar-más-check del resto del proyecto en vez de un tipo enum nativo de PostgreSQL.
 */
public enum RecurringFrequency {
    MONTHLY,
    WEEKLY
}
