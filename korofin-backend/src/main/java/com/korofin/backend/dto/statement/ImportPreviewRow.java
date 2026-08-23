package com.korofin.backend.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de la previsualización de importación de un extracto bancario, tal como se muestra al
 * usuario antes de confirmar la importación.
 *
 * @param date                  fecha del movimiento
 * @param description           descripción tal como aparece en el extracto
 * @param amount                monto del movimiento, siempre positivo
 * @param movementType          si el movimiento es un ingreso o un gasto
 * @param isDuplicate           {@code true} si {@code DuplicateDetector} lo identificó como
 *                              probable duplicado de un movimiento ya registrado
 * @param suggestedCategoryId   id de la categoría sugerida, o {@code null} si no hay coincidencia
 * @param suggestedCategoryName nombre de la categoría sugerida, o {@code null} si no hay coincidencia
 */
public record ImportPreviewRow(
        LocalDate date,
        String description,
        BigDecimal amount,
        MovementType movementType,
        boolean isDuplicate,
        Long suggestedCategoryId,
        String suggestedCategoryName
) {
}
