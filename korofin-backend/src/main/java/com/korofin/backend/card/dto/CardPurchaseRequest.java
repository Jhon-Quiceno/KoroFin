package com.korofin.backend.card.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para registrar una compra
 * ({@link com.korofin.backend.card.entity.CardMovementType#PURCHASE} o
 * {@code INSTALLMENT_PURCHASE}) contra una tarjeta.
 *
 * @param amount           monto de la compra, debe ser positivo; el servicio además rechaza
 *                         montos que superen el cupo disponible de la tarjeta
 * @param date             fecha de la compra; por defecto hoy cuando se omite, no puede ser
 *                         futura
 * @param description      descripción libre opcional
 * @param installmentCount número de cuotas. {@code null} o {@code 1} = compra simple; entre 2 y
 *                         48 genera una compra diferida ({@code INSTALLMENT_PURCHASE} +
 *                         {@code InstallmentPlan}, ver {@code AmortizationService})
 */
public record CardPurchaseRequest(
        @NotNull(message = "El monto de la compra es obligatorio")
        @Positive(message = "El monto de la compra debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate date,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description,
        @Min(value = 1, message = "El número de cuotas debe ser al menos 1")
        @Max(value = 48, message = "El número de cuotas no puede superar 48")
        Integer installmentCount
) {
}
