package com.korofin.backend.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de {@code POST /api/integrations/telegram/receipts}, reenviado por n8n (protegido por
 * {@code TelegramWebhookFilter}, sin JWT) con la foto de un recibo de un chat ya vinculado.
 *
 * @param telegramChatId el chat que envió la foto
 * @param imageDataUri   la imagen como data URI ({@code data:image/...;base64,...}), mismo formato
 *                       que {@code ReceiptScanRequest} (dominio {@code ai})
 */
public record TelegramReceiptRequest(
        @NotNull(message = "El chatId es obligatorio")
        Long telegramChatId,
        @NotBlank(message = "La imagen es obligatoria")
        String imageDataUri
) {
}
