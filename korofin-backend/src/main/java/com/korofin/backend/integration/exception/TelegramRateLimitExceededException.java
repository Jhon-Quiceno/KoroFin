package com.korofin.backend.integration.exception;

/**
 * Se lanza cuando un {@code chatId} de Telegram supera su propio límite de mensajes en la ventana
 * configurada (ver {@code TelegramExpenseService}, que instancia su propio
 * {@code InMemoryRateLimiter} interno — NO el {@code RateLimitFilter} global de la fase 1, que no
 * conoce las rutas de Telegram). Mapeada a {@code 429 Too Many Requests}.
 */
public class TelegramRateLimitExceededException extends RuntimeException {

    public TelegramRateLimitExceededException(String message) {
        super(message);
    }
}
