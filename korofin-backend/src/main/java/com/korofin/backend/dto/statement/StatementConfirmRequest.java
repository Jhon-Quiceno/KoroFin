package com.korofin.backend.dto.statement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload de {@code POST /api/statement-imports/confirm}: la lista final de movimientos que el
 * usuario decidió importar, luego de revisar la previsualización.
 *
 * @param rows filas a crear como ingresos o gastos, no puede estar vacía
 */
public record StatementConfirmRequest(
        @NotEmpty(message = "Debe confirmar al menos un movimiento")
        @Valid
        List<ImportConfirmRow> rows
) {
}
