package com.korofin.backend.dto.ai;

import com.korofin.backend.entity.ai.AiUsageEventType;

import java.time.YearMonth;
import java.util.Map;

/**
 * Desglose por periodo de {@code ai_usage_events} para el usuario actual, devuelto por
 * {@code AiUsageEventService#getUsageSummary}.
 *
 * <p>Distinto de {@link AiUsageResponse}: ese reporta la cuota mensual de chat de rol USER
 * ({@code app.ai.monthly-message-limit}); este reporta el consumo bruto de tokens a través de
 * toda operación de IA rastreada (chat, categorize, insight), sin importar la cuota de chat.
 *
 * @param period       el mes calendario UTC que cubre este resumen
 * @param totalTokens  suma de {@code tokensUsed} en todos los eventos de {@code period}
 * @param totalEvents  cantidad total de eventos rastreados en {@code period}
 * @param eventsByType cantidad de eventos por {@link AiUsageEventType} en {@code period}
 */
public record AiUsageEventSummaryResponse(
        YearMonth period,
        long totalTokens,
        long totalEvents,
        Map<AiUsageEventType, Long> eventsByType
) {
}
