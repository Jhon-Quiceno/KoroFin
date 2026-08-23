package com.korofin.backend.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de {@code POST /api/integrations/telegram/expenses}, reenviado por n8n (protegido por
 * {@code TelegramWebhookFilter}, sin JWT) con el texto de un mensaje de un chat ya vinculado.
 *
 * @param telegramChatId el chat que envió el mensaje
 * @param text           el texto libre del mensaje (una pregunta o un intento de registro)
 */
public record TelegramMessageRequest(
        @NotNull(message = "El chatId es obligatorio")
        Long telegramChatId,
        @NotBlank(message = "El texto del mensaje es obligatorio")
        String text
) {
}
