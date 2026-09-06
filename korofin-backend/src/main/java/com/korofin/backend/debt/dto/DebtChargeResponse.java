package com.korofin.backend.debt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lectura de un {@link com.korofin.backend.debt.entity.DebtCharge}.
 *
 * @param id          identificador del cargo
 * @param debtId      identificador de la deuda a la que se aplicó el cargo
 * @param amount      monto del cargo
 * @param chargeDate  fecha del cargo
 * @param description descripción libre opcional
 * @param createdAt   marca de tiempo de creación
 */
public record DebtChargeResponse(
        Long id,
        Long debtId,
        BigDecimal amount,
        LocalDate chargeDate,
        String description,
        Instant createdAt
) {
}
