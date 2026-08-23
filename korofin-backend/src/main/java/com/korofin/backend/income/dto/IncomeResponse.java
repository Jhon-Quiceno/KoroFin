package com.korofin.backend.income.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modelo de lectura de un {@link com.korofin.backend.income.entity.Income}.
 *
 * @param id           identificador del ingreso
 * @param amount       monto del ingreso
 * @param description  nota libre opcional
 * @param date         fecha en la que se recibió el ingreso
 * @param categoryId   identificador de la categoría asociada, o {@code null} si no está clasificado
 * @param categoryName nombre de la categoría asociada, o {@code null} si no está clasificado
 */
public record IncomeResponse(
        Long id,
        BigDecimal amount,
        String description,
        LocalDate date,
        Long categoryId,
        String categoryName
) {
}
