package com.korofin.backend.ai.controller;

import com.korofin.backend.ai.dto.InsightResponse;
import com.korofin.backend.ai.service.AiInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de insights financieros generados por IA: {@code GET /api/ai/insights} devuelve
 * el insight más reciente del usuario actual (si hay), {@code POST /api/ai/insights/generate}
 * genera y persiste uno nuevo.
 */
@RestController
@RequestMapping("/api/ai/insights")
public class AiInsightController {

    private final AiInsightService aiInsightService;

    public AiInsightController(AiInsightService aiInsightService) {
        this.aiInsightService = aiInsightService;
    }

    @GetMapping
    public ResponseEntity<InsightResponse> getLatest() {
        InsightResponse response = aiInsightService.getLatestInsight();
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @PostMapping("/generate")
    public ResponseEntity<InsightResponse> generate() {
        return ResponseEntity.ok(aiInsightService.generateInsight());
    }
}
