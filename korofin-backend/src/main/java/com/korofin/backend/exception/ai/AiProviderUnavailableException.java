package com.korofin.backend.exception.ai;

/**
 * Lanzada cuando el proveedor de IA no se puede alcanzar en absoluto (conexión rechazada, falla
 * de DNS) o responde con un error de servidor (HTTP 5xx) que no está clasificado de forma más
 * específica por otra subclase de {@link AiProviderException}.
 */
public class AiProviderUnavailableException extends AiProviderException {

    public AiProviderUnavailableException(String providerName) {
        super(
                providerName,
                "El proveedor de IA no está disponible en este momento. Intenta de nuevo más tarde."
        );
    }
}
