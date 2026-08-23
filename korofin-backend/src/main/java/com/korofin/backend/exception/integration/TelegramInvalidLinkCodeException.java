package com.korofin.backend.exception.integration;

/**
 * Se lanza al confirmar un vínculo con un código de un solo uso que no existe, ya se consumió, o
 * expiró (ver {@code TelegramLinkCodeStore}). Mapeada a {@code 400 Bad Request}: el código lo
 * escribe una persona a mano en Telegram, así que un typo o un código vencido es un error de
 * entrada del cliente, no un recurso ausente.
 */
public class TelegramInvalidLinkCodeException extends RuntimeException {

    public TelegramInvalidLinkCodeException(String message) {
        super(message);
    }
}
