package com.korofin.backend.controller.ai;

import com.korofin.backend.dto.ai.AiProviderStatusResponse;
import com.korofin.backend.service.ai.provider.AiProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Estado de solo lectura de los proveedores de IA a nivel de aplicación, configurados vía
 * variables de entorno. Deliberadamente no expone ningún endpoint de mutación: los proveedores
 * los configura el operador de la app, no los usuarios finales.
 */
@RestController
@RequestMapping("/api/ai/providers")
public class AiProviderStatusController {

    private final AiProviderRegistry registry;

    public AiProviderStatusController(AiProviderRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/status")
    public ResponseEntity<List<AiProviderStatusResponse>> status() {
        List<AiProviderStatusResponse> response = registry.status().stream()
                .map(status -> new AiProviderStatusResponse(status.name(), status.configured(), status.priority()))
                .toList();
        return ResponseEntity.ok(response);
    }
}
