package com.korofin.backend.dto.card;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload para editar una {@link com.korofin.backend.entity.card.CreditCard} existente.
 *
 * <p>Excluye deliberadamente {@code franchise}, {@code creditLimit} y {@code currentBalance}: la
 * franquicia y el cupo quedan fijos desde la creación, y el saldo solo puede cambiar a través de
 * un {@link com.korofin.backend.entity.card.CardMovement} registrado — mismo criterio de
 * trazabilidad por el que {@link com.korofin.backend.dto.debt.DebtUpdateRequest} excluye
 * {@code totalAmount}/{@code remainingAmount}.
 *
 * <p>{@code monthlyRate} sí es editable, pero el cambio nunca es retroactivo: cada
 * {@link com.korofin.backend.entity.card.InstallmentPlan} ya creado conserva su
 * {@code rateAtPurchase} congelado.
 *
 * @param name          nombre o descripción de la tarjeta
 * @param bank          banco emisor, opcional
 * @param monthlyRate   tasa mensual efectiva, no negativa
 * @param cutoffDay     día del mes en el que corta el ciclo de facturación, 1-31
 * @param paymentDueDay día del mes en el que vence el pago, 1-31
 */
public record CreditCardUpdateRequest(
        @NotBlank(message = "El nombre de la tarjeta es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String name,
        @Size(max = 100, message = "El banco no puede superar 100 caracteres")
        String bank,
        @NotNull(message = "La tasa mensual es obligatoria")
        @DecimalMin(value = "0.0", message = "La tasa mensual no puede ser negativa")
        BigDecimal monthlyRate,
        @NotNull(message = "El día de corte es obligatorio")
        @Min(value = 1, message = "El día de corte debe estar entre 1 y 31")
        @Max(value = 31, message = "El día de corte debe estar entre 1 y 31")
        Integer cutoffDay,
        @NotNull(message = "El día de pago es obligatorio")
        @Min(value = 1, message = "El día de pago debe estar entre 1 y 31")
        @Max(value = 31, message = "El día de pago debe estar entre 1 y 31")
        Integer paymentDueDay
) {
}
