package com.korofin.backend.controller.ai;

import com.korofin.backend.dto.ai.CategorizeRequest;
import com.korofin.backend.dto.ai.CategorizeResponse;
import com.korofin.backend.service.ai.AiCategorizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de sugerencia de categoría de gastos/ingresos asistida por IA.
 */
@RestController
@RequestMapping("/api/ai/categorize")
public class AiCategorizationController {

    private final AiCategorizationService service;

    public AiCategorizationController(AiCategorizationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CategorizeResponse> categorize(@Valid @RequestBody CategorizeRequest request) {
        return ResponseEntity.ok(service.categorize(request));
    }
}
