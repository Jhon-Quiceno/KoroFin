package com.korofin.backend.dto.debt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lectura de una {@link com.korofin.backend.entity.debt.Debt}.
 *
 * @param id              identificador de la deuda
 * @param name            nombre o descripción de la deuda
 * @param totalAmount     monto total adeudado, fijo desde la creación
 * @param remainingAmount saldo pendiente actual, solo modificable vía abonos y cargos registrados
 * @param interestRate    tasa de interés opcional en porcentaje
 * @param dueDate         fecha de vencimiento opcional
 * @param createdAt       marca de tiempo de creación
 * @param updatedAt       marca de tiempo de la última actualización
 */
public record DebtResponse(
        Long id,
        String name,
        BigDecimal totalAmount,
        BigDecimal remainingAmount,
        BigDecimal interestRate,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {
}
