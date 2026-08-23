package com.korofin.backend.ai.exception;

/**
 * Lanzada cuando todos los proveedores de IA configurados fallaron al responder un request (ver
 * {@code com.korofin.backend.ai.service.provider.AiChatOrchestrator}).
 *
 * <p>Deliberadamente NO extiende {@link AiProviderException}: a diferencia de esa jerarquía, esta
 * excepción nunca debe cargar ni filtrar qué proveedor específico falló, así que no puede
 * confundirse con un error por-proveedor en ningún punto de la cadena de excepciones.
 */
public class AiProvidersExhaustedException extends RuntimeException {

    public AiProvidersExhaustedException(String message) {
        super(message);
    }
}
