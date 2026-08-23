package com.korofin.backend.statement.dto;

import java.util.List;

/**
 * Respuesta de {@code POST /api/statement-imports/preview}: los movimientos extraídos del
 * extracto, listos para que el usuario los revise, edite y confirme. No persiste nada.
 *
 * @param rows          movimientos extraídos, en el mismo orden en que aparecen en el extracto
 * @param totalRows     cantidad total de filas extraídas (equivale a {@code rows.size()})
 * @param duplicateRows cantidad de filas marcadas como posible duplicado
 */
public record StatementPreviewResponse(
        List<ImportPreviewRow> rows,
        int totalRows,
        int duplicateRows
) {
}
