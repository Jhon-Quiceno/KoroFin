package com.korofin.backend.debt.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para registrar un {@link com.korofin.backend.debt.entity.DebtCharge} (cargo) contra una
 * deuda.
 *
 * @param amount      monto del cargo, debe ser estrictamente positivo; incrementa el saldo
 *                    restante de la deuda en vez de reducirlo (espejo de
 *                    {@link DebtPaymentRequest})
 * @param chargeDate  fecha del cargo; por defecto hoy cuando se omite, no puede ser futura
 * @param description descripción libre opcional
 */
public record DebtChargeRequest(
        @NotNull(message = "El monto del cargo es obligatorio")
        @Positive(message = "El monto del cargo debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate chargeDate,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description
) {
}
