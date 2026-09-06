package com.korofin.backend.ai.controller;

import com.korofin.backend.ai.dto.AiUsageEventSummaryResponse;
import com.korofin.backend.ai.service.AiUsageEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Endpoint REST del rastreo granular de uso de IA ({@code ai_usage_events}):
 * {@code GET /api/ai/usage} reporta el consumo de tokens a través de toda operación de IA
 * rastreada (chat, categorize, insight) para un mes calendario UTC dado, usando el mes actual por
 * defecto cuando se omite {@code period}.
 *
 * <p>Distinto de {@code GET /api/ai/chat/usage}, que reporta la cuota mensual de mensajes de
 * chat.
 */
@RestController
@RequestMapping("/api/ai/usage")
public class AiUsageEventController {

    private final AiUsageEventService aiUsageEventService;

    public AiUsageEventController(AiUsageEventService aiUsageEventService) {
        this.aiUsageEventService = aiUsageEventService;
    }

    @GetMapping
    public ResponseEntity<AiUsageEventSummaryResponse> getUsageSummary(
            @RequestParam(required = false) String period
    ) {
        YearMonth resolvedPeriod = parsePeriod(period);
        return ResponseEntity.ok(aiUsageEventService.getUsageSummary(resolvedPeriod));
    }

    private static YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(ZoneOffset.UTC);
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("El periodo debe tener el formato yyyy-MM");
        }
    }
}
