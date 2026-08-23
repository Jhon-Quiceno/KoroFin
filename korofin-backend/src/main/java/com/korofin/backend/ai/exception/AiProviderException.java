package com.korofin.backend.ai.exception;

import com.korofin.backend.common.exception.GlobalExceptionHandler;

/**
 * Tipo base para toda falla al llamar a un proveedor de IA configurado por el operador de la app,
 * a través de {@code com.korofin.backend.ai.service.provider.AiChatClient}.
 *
 * <p>Las subclases distinguen el modo de falla específico (autenticación, límite de uso, modelo
 * no encontrado, timeout, o indisponibilidad general) con un mensaje factual, en español, de uso
 * interno/diagnóstico. En operación normal estas nunca llegan directo a un controller: las
 * captura {@code com.korofin.backend.ai.service.provider.AiChatOrchestrator}, que prueba el
 * siguiente proveedor configurado y solo expone
 * {@code AiChatOrchestrator.GENERIC_MESSAGE} al usuario final una vez que todos fallaron. Los
 * mapeos por subclase en {@link GlobalExceptionHandler} quedan como respaldo defensivo.
 */
public class AiProviderException extends RuntimeException {

    private final String providerName;

    public AiProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public AiProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    /**
     * @return el nombre del proveedor de IA que lanzó esta falla (p. ej. {@code "nvidia"})
     */
    public String getProviderName() {
        return providerName;
    }
}
