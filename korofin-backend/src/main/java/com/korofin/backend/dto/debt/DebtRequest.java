package com.korofin.backend.dto.debt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para crear una {@link com.korofin.backend.entity.debt.Debt}.
 *
 * @param name         nombre o descripción de la deuda
 * @param totalAmount  monto total adeudado, debe ser estrictamente positivo; también inicializa
 *                     {@code remainingAmount} al crear la deuda
 * @param interestRate tasa de interés opcional en porcentaje, no puede ser negativa
 * @param dueDate      fecha de vencimiento opcional
 */
public record DebtRequest(
        @NotBlank(message = "El nombre de la deuda es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String name,
        @NotNull(message = "El monto total es obligatorio")
        @Positive(message = "El monto total debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto total no puede tener más de 2 decimales")
        BigDecimal totalAmount,
        @DecimalMin(value = "0.0", message = "La tasa de interés no puede ser negativa")
        BigDecimal interestRate,
        LocalDate dueDate
) {
}
