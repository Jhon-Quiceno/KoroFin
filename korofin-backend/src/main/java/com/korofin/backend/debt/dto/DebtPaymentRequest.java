package com.korofin.backend.debt.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para registrar un {@link com.korofin.backend.debt.entity.DebtPayment} (abono) contra
 * una deuda.
 *
 * @param amount      monto del abono, debe ser estrictamente positivo; el servicio además rechaza
 *                    montos que superen el saldo restante actual de la deuda
 * @param paymentDate fecha del abono; por defecto hoy cuando se omite, no puede ser futura
 * @param note        nota libre opcional
 */
public record DebtPaymentRequest(
        @NotNull(message = "El monto del abono es obligatorio")
        @Positive(message = "El monto del abono debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate paymentDate,
        @Size(max = 255, message = "La nota no puede superar 255 caracteres")
        String note
) {
}
