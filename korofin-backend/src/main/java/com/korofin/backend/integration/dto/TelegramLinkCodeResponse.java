package com.korofin.backend.integration.dto;

/**
 * Respuesta de {@code POST /api/integrations/telegram/link-code}: el código de un solo uso que el
 * usuario debe enviarle al bot de Telegram para completar el vínculo, y su tiempo de vida.
 *
 * @param code           código de un solo uso, TTL corto (ver {@code TelegramLinkCodeStore})
 * @param expiresInSeconds cuánto tiempo, en segundos, es válido el código desde que se generó
 */
public record TelegramLinkCodeResponse(
        String code,
        long expiresInSeconds
) {
}
