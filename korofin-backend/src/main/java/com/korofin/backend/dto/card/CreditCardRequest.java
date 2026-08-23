package com.korofin.backend.dto.card;

import com.korofin.backend.entity.card.CardFranchise;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload para crear una {@link com.korofin.backend.entity.card.CreditCard}.
 *
 * @param name          nombre o descripción de la tarjeta
 * @param bank          banco emisor, opcional
 * @param franchise     franquicia (red de pagos)
 * @param creditLimit   cupo total, debe ser estrictamente positivo
 * @param monthlyRate   tasa mensual efectiva (ej. {@code 0.0250} para 2,5% E.M.), no negativa
 * @param cutoffDay     día del mes en el que corta el ciclo de facturación, 1-31
 * @param paymentDueDay día del mes en el que vence el pago, 1-31
 */
public record CreditCardRequest(
        @NotBlank(message = "El nombre de la tarjeta es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String name,
        @Size(max = 100, message = "El banco no puede superar 100 caracteres")
        String bank,
        @NotNull(message = "La franquicia es obligatoria")
        CardFranchise franchise,
        @NotNull(message = "El cupo es obligatorio")
        @Positive(message = "El cupo debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El cupo no puede tener más de 2 decimales")
        BigDecimal creditLimit,
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
