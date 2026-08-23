package com.korofin.backend.dto.card;

import com.korofin.backend.entity.card.CardMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lectura de un {@link com.korofin.backend.entity.card.CardMovement}.
 *
 * @param id                identificador del movimiento
 * @param cardId            identificador de la tarjeta a la que pertenece el movimiento
 * @param type              tipo de movimiento; determina el efecto sobre el saldo
 * @param amount            monto del movimiento, siempre positivo
 * @param date              fecha del movimiento
 * @param description       descripción libre opcional
 * @param cardBalanceAfter  saldo de la tarjeta justo después de aplicar este movimiento. Queda
 *                          {@code null} al listar movimientos históricos: el saldo actual de la
 *                          tarjeta no representa el saldo posterior a un movimiento viejo
 * @param expenseId         identificador del {@code Expense} creado junto con este movimiento
 *                          (solo compras; {@code null} en pagos y en el listado histórico)
 * @param installmentPlanId identificador del plan de cuotas asociado (solo compras diferidas a
 *                          2+ cuotas; {@code null} en compras simples, pagos y el listado)
 * @param createdAt         marca de tiempo de creación
 */
public record CardMovementResponse(
        Long id,
        Long cardId,
        CardMovementType type,
        BigDecimal amount,
        LocalDate date,
        String description,
        BigDecimal cardBalanceAfter,
        Long expenseId,
        Long installmentPlanId,
        Instant createdAt
) {
}
