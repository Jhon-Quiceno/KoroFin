package com.korofin.backend.exception.ai;

/**
 * Lanzada cuando el usuario actual ya envió {@code app.ai.monthly-message-limit} mensajes de chat
 * de rol USER en el mes calendario UTC actual (ver {@code AiChatService#chat}), antes de contactar
 * a ningún proveedor de IA.
 */
public class AiMessageQuotaExceededException extends RuntimeException {

    public AiMessageQuotaExceededException(String message) {
        super(message);
    }
}
