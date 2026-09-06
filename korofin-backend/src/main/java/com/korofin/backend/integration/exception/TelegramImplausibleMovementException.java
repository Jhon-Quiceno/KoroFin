package com.korofin.backend.integration.exception;

/**
 * Se lanza cuando {@code TelegramMessageParser} extrae un monto de un mensaje de Telegram, pero
 * ese monto es implausible (absurdamente bajo o alto para ser un movimiento financiero real), o
 * cuando no se pudo identificar ningún monto en el texto. Mapeada a {@code 422 Unprocessable
 * Entity} con un mensaje amigable que n8n reenvía tal cual al chat de Telegram (ver
 * docs/backend-plan.md sección 5).
 */
public class TelegramImplausibleMovementException extends RuntimeException {

    public TelegramImplausibleMovementException(String message) {
        super(message);
    }
}
