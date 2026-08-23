package com.korofin.backend.controller.integration;

import com.korofin.backend.dto.integration.TelegramConfirmLinkRequest;
import com.korofin.backend.dto.integration.TelegramLinkCodeResponse;
import com.korofin.backend.dto.integration.TelegramMessageRequest;
import com.korofin.backend.dto.integration.TelegramReceiptRequest;
import com.korofin.backend.dto.integration.TelegramReplyResponse;
import com.korofin.backend.service.integration.telegram.TelegramExpenseService;
import com.korofin.backend.service.integration.telegram.TelegramLinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de la integración de Telegram (dominio {@code integration}, fase 7).
 *
 * <p>{@link #generateLinkCode()} está protegido por JWT estándar (lo llama la app, con sesión de
 * usuario). {@link #confirmLink}, {@link #registerExpenseFromMessage} y
 * {@link #registerExpenseFromReceipt} están protegidos por {@code TelegramWebhookFilter} en vez de
 * JWT — n8n es servidor-a-servidor, sin sesión de usuario (ver {@code SecurityConfig} y
 * docs/backend-plan.md sección 5).
 */
@RestController
@RequestMapping("/api/integrations/telegram")
public class TelegramIntegrationController {

    private final TelegramLinkService telegramLinkService;
    private final TelegramExpenseService telegramExpenseService;

    public TelegramIntegrationController(
            TelegramLinkService telegramLinkService,
            TelegramExpenseService telegramExpenseService
    ) {
        this.telegramLinkService = telegramLinkService;
        this.telegramExpenseService = telegramExpenseService;
    }

    @PostMapping("/link-code")
    public ResponseEntity<TelegramLinkCodeResponse> generateLinkCode() {
        return ResponseEntity.ok(telegramLinkService.generateLinkCode());
    }

    @PostMapping("/confirm-link")
    public ResponseEntity<Void> confirmLink(@Valid @RequestBody TelegramConfirmLinkRequest request) {
        telegramLinkService.confirmLink(request.code(), request.telegramChatId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/expenses")
    public ResponseEntity<TelegramReplyResponse> registerExpenseFromMessage(
            @Valid @RequestBody TelegramMessageRequest request
    ) {
        return ResponseEntity.ok(
                telegramExpenseService.registerFromMessage(request.telegramChatId(), request.text())
        );
    }

    @PostMapping("/receipts")
    public ResponseEntity<TelegramReplyResponse> registerExpenseFromReceipt(
            @Valid @RequestBody TelegramReceiptRequest request
    ) {
        return ResponseEntity.ok(
                telegramExpenseService.registerFromReceipt(request.telegramChatId(), request.imageDataUri())
        );
    }
}
