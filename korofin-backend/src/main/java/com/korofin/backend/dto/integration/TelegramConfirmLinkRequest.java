package com.korofin.backend.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de {@code POST /api/integrations/telegram/confirm-link}, reenviado por n8n (protegido
 * por {@code TelegramWebhookFilter}, sin JWT) una vez que el usuario le mandó su código de vínculo
 * al bot de Telegram.
 *
 * @param code           el código de un solo uso generado por {@code POST .../link-code}
 * @param telegramChatId el chat de Telegram que envió el código
 */
public record TelegramConfirmLinkRequest(
        @NotBlank(message = "El código es obligatorio")
        String code,
        @NotNull(message = "El chatId es obligatorio")
        Long telegramChatId
) {
}
