package com.korofin.backend.dto.integration;

/**
 * Respuesta de {@code POST /api/integrations/telegram/expenses} y {@code .../receipts}: un único
 * mensaje de texto (español) que n8n reenvía tal cual al chat de Telegram como respuesta al
 * usuario — nunca estructura que n8n tenga que interpretar/formatear, todo el texto ya está listo
 * para mostrarse.
 *
 * @param message texto de respuesta, orientado al usuario
 */
public record TelegramReplyResponse(
        String message
) {
}
