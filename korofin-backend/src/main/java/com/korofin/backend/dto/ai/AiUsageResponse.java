package com.korofin.backend.dto.ai;

import java.time.Instant;

/**
 * Respuesta de {@code GET /api/ai/chat/usage}: el uso de cuota de chat de IA del usuario actual
 * para el mes calendario UTC en curso.
 *
 * @param used      cantidad de mensajes de chat de IA de rol USER enviados en lo que va del mes
 *                  calendario UTC actual
 * @param limit     cantidad máxima de mensajes de chat de IA de rol USER permitidos por mes
 *                  calendario UTC (ver {@code app.ai.monthly-message-limit})
 * @param remaining mensajes restantes antes de llegar a {@code limit}, nunca negativo
 * @param resetsAt  instante en que se reinicia la cuota — inicio del próximo mes calendario UTC
 */
public record AiUsageResponse(
        int used,
        int limit,
        int remaining,
        Instant resetsAt
) {
}
