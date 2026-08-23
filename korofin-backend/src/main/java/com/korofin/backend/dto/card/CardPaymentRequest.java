package com.korofin.backend.dto.card;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para registrar un pago
 * ({@link com.korofin.backend.entity.card.CardMovementType#PAYMENT}) contra una tarjeta.
 *
 * @param amount      monto del pago, debe ser positivo; el servicio además rechaza montos que
 *                    superen el saldo actual de la tarjeta
 * @param date        fecha del pago; por defecto hoy cuando se omite, no puede ser futura
 * @param description descripción libre opcional
 */
public record CardPaymentRequest(
        @NotNull(message = "El monto del pago es obligatorio")
        @Positive(message = "El monto del pago debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate date,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description
) {
}
