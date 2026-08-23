package com.korofin.backend.dto.ai;

import java.time.Instant;

/**
 * Modelo de lectura de una fila
 * {@link com.korofin.backend.entity.ai.AiMessageKind#INSIGHT}, devuelto por
 * {@code GET /api/ai/insights} (la más reciente) y {@code POST /api/ai/insights/generate} (la
 * recién generada).
 *
 * @param id           identificador del insight
 * @param content      el texto de recomendaciones generado por IA, en español, con viñetas
 * @param providerName proveedor de IA que generó este insight
 * @param model        modelo que generó este insight
 * @param createdAt    instante en que se generó este insight
 */
public record InsightResponse(
        Long id,
        String content,
        String providerName,
        String model,
        Instant createdAt
) {
}
