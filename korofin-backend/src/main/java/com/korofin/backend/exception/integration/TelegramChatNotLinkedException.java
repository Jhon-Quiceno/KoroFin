package com.korofin.backend.exception.integration;

import com.korofin.backend.exception.ResourceNotFoundException;

/**
 * Se lanza cuando un mensaje o recibo llega desde un {@code chatId} de Telegram que todavía no
 * completó el flujo de vínculo (ver docs/backend-plan.md sección 5). Extiende
 * {@link ResourceNotFoundException} para reusar su mapeo a {@code 404 Not Found} en
 * {@code GlobalExceptionHandler} sin duplicar un handler — mismo criterio que
 * {@code DebtNotFoundException}.
 */
public class TelegramChatNotLinkedException extends ResourceNotFoundException {

    private static final String DEFAULT_MESSAGE =
            "Este chat todavía no está vinculado a una cuenta de KoroFin. Generá un código de vínculo desde la app.";

    public TelegramChatNotLinkedException() {
        super(DEFAULT_MESSAGE);
    }
}
