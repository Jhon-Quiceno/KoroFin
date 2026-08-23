package com.korofin.backend.recurringpayment.dto;

import com.korofin.backend.recurringpayment.entity.RecurringFrequency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lectura de un {@link com.korofin.backend.recurringpayment.entity.RecurringPayment}.
 *
 * <p>El componente se llama {@code isActive} (en vez de {@code active}) para que serialice a un
 * campo JSON {@code isActive}.
 *
 * @param id              identificador del pago recurrente
 * @param name            nombre/descripción del pago recurrente
 * @param amount          monto que se cobra cada ciclo
 * @param frequency       ciclo de facturación
 * @param nextPaymentDate fecha de la próxima ejecución programada
 * @param isActive        si el pago recurrente está activo actualmente
 * @param createdAt       fecha de creación
 * @param updatedAt       fecha de última actualización
 */
public record RecurringPaymentResponse(
        Long id,
        String name,
        BigDecimal amount,
        RecurringFrequency frequency,
        LocalDate nextPaymentDate,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
