package com.korofin.backend.dto.debt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para editar una {@link com.korofin.backend.entity.debt.Debt} existente.
 *
 * <p>Excluye deliberadamente {@code totalAmount} y {@code remainingAmount}: el saldo restante
 * solo puede cambiar a través de un {@link com.korofin.backend.entity.debt.DebtPayment} (lo
 * reduce) o un {@link com.korofin.backend.entity.debt.DebtCharge} (lo incrementa), nunca por una
 * sobrescritura sin registro. Es un requisito de trazabilidad, no una omisión: el campo no está
 * en el DTO justamente para que no exista forma de mandarlo. Un cliente que igual lo incluya en
 * el JSON lo ve ignorado en silencio, sin efecto sobre la deuda.
 *
 * @param name         nombre o descripción de la deuda
 * @param interestRate tasa de interés opcional en porcentaje, no puede ser negativa
 * @param dueDate      fecha de vencimiento opcional
 */
public record DebtUpdateRequest(
        @NotBlank(message = "El nombre de la deuda es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String name,
        @DecimalMin(value = "0.0", message = "La tasa de interés no puede ser negativa")
        BigDecimal interestRate,
        LocalDate dueDate
) {
}
