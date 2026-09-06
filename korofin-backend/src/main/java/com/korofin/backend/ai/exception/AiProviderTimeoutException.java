package com.korofin.backend.ai.exception;

/**
 * Lanzada cuando el proveedor de IA no responde dentro de los timeouts de conexión/lectura
 * configurados (ver {@code AiChatClient}, 10s conexión / {@code app.ai.read-timeout-seconds}
 * lectura).
 */
public class AiProviderTimeoutException extends AiProviderException {

    public AiProviderTimeoutException(String providerName) {
        super(
                providerName,
                "El proveedor de IA no respondió a tiempo. Intenta de nuevo."
        );
    }
}
