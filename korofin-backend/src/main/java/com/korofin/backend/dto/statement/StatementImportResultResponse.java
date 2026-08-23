package com.korofin.backend.dto.statement;

/**
 * Respuesta de {@code POST /api/statement-imports/confirm}.
 *
 * @param createdCount cantidad de ingresos/gastos creados
 */
public record StatementImportResultResponse(int createdCount) {
}
