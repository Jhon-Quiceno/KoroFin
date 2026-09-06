package com.korofin.backend.ai.exception;

/**
 * Lanzada cuando el proveedor de IA reporta que el modelo configurado no existe o no es accesible
 * con la API key actual (HTTP 404, o un cuerpo de error no-404 que menciona el modelo).
 *
 * <p>Capturada por {@code com.korofin.backend.ai.service.provider.AiChatOrchestrator}, que prueba
 * el siguiente proveedor configurado — este mensaje es solo interno/diagnóstico.
 */
public class AiProviderModelNotFoundException extends AiProviderException {

    public AiProviderModelNotFoundException(String providerName) {
        super(
                providerName,
                "El modelo configurado no está disponible en el proveedor de IA."
        );
    }
}
