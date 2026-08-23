package com.korofin.backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un movimiento (gasto o ingreso) del período, para la tabla de {@code GET /api/reports/movements}
 * y para la exportación de {@code GET /api/reports/export}.
 *
 * @param id           identificador del gasto/ingreso original
 * @param type         si es un gasto o un ingreso
 * @param date         fecha del movimiento
 * @param amount       monto del movimiento
 * @param description  nota libre opcional
 * @param categoryName nombre de la categoría, o {@code "Sin categoría"} si no está clasificado
 */
public record MovementResponse(
        Long id,
        MovementType type,
        LocalDate date,
        BigDecimal amount,
        String description,
        String categoryName
) {
}
