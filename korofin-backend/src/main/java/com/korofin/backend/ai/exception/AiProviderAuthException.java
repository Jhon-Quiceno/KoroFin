package com.korofin.backend.ai.exception;

/**
 * Lanzada cuando el proveedor de IA rechaza el request con un error de autenticación/autorización
 * (HTTP 401 o 403): la API key configurada por el operador (vía variables de entorno) es
 * inválida, fue revocada, o no tiene permiso para el modelo pedido.
 *
 * <p>Capturada por {@code com.korofin.backend.ai.service.provider.AiChatOrchestrator}, que prueba
 * el siguiente proveedor configurado — este mensaje es solo interno/diagnóstico (de log), nunca
 * llega directo al usuario final.
 */
public class AiProviderAuthException extends AiProviderException {

    public AiProviderAuthException(String providerName) {
        super(
                providerName,
                "La API key configurada por el operador para este proveedor de IA fue rechazada."
        );
    }
}
