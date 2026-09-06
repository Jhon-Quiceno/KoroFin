package com.korofin.backend.card.dto;

import com.korofin.backend.card.entity.CardFranchise;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lectura de una {@link com.korofin.backend.card.entity.CreditCard}.
 *
 * @param id              identificador de la tarjeta
 * @param name            nombre o descripción de la tarjeta
 * @param bank            banco emisor, puede ser {@code null}
 * @param franchise       franquicia (red de pagos)
 * @param creditLimit     cupo total, fijo desde la creación
 * @param monthlyRate     tasa mensual efectiva vigente
 * @param cutoffDay       día del mes en el que corta el ciclo de facturación
 * @param paymentDueDay   día del mes en el que vence el pago
 * @param currentBalance  saldo actual, solo modificable vía movimientos registrados
 * @param availableCredit cupo disponible, derivado como {@code creditLimit - currentBalance}
 * @param lastCutoffDate  fecha del último ciclo cerrado, {@code null} si nunca se cerró uno
 * @param createdAt       marca de tiempo de creación
 * @param updatedAt       marca de tiempo de la última actualización
 */
public record CreditCardResponse(
        Long id,
        String name,
        String bank,
        CardFranchise franchise,
        BigDecimal creditLimit,
        BigDecimal monthlyRate,
        Integer cutoffDay,
        Integer paymentDueDay,
        BigDecimal currentBalance,
        BigDecimal availableCredit,
        LocalDate lastCutoffDate,
        Instant createdAt,
        Instant updatedAt
) {
}
