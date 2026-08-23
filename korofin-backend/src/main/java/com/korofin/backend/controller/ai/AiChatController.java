package com.korofin.backend.controller.ai;

import com.korofin.backend.dto.ai.AiUsageResponse;
import com.korofin.backend.dto.ai.ChatMessageResponse;
import com.korofin.backend.dto.ai.ChatReplyResponse;
import com.korofin.backend.dto.ai.ChatRequest;
import com.korofin.backend.service.ai.AiChatService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST del chat de IA del usuario actual: {@code POST /api/ai/chat} envía un mensaje y
 * obtiene una respuesta, {@code GET /api/ai/chat/history} lista turnos pasados, y
 * {@code GET /api/ai/chat/usage} reporta el uso de la cuota mensual de mensajes.
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public ResponseEntity<ChatReplyResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(aiChatService.chat(request));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<ChatMessageResponse>> getHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(aiChatService.getHistory(pageable));
    }

    @GetMapping("/usage")
    public ResponseEntity<AiUsageResponse> getUsage() {
        return ResponseEntity.ok(aiChatService.getUsage());
    }
}
