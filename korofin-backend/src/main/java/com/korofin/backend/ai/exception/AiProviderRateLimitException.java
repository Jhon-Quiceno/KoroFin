package com.korofin.backend.ai.exception;

/**
 * Lanzada cuando el proveedor de IA rechaza el request con un error de límite de uso (HTTP 429),
 * típicamente porque la cuota gratuita del usuario/operador (p. ej. ~10 requests/minuto de
 * Gemini) fue superada.
 */
public class AiProviderRateLimitException extends AiProviderException {

    public AiProviderRateLimitException(String providerName) {
        super(
                providerName,
                "El proveedor de IA alcanzó su límite de uso. Intenta de nuevo en unos minutos."
        );
    }
}
