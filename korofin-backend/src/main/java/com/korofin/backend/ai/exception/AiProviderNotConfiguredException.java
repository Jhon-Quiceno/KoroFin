package com.korofin.backend.ai.exception;

/**
 * Lanzada cuando se invoca una funcionalidad de IA (chat, insights, categorización) pero el
 * operador de la app no configuró ningún proveedor todavía (ver
 * {@code com.korofin.backend.ai.service.provider.AiProviderRegistry}).
 *
 * <p>A diferencia de {@link AiProviderException} y sus subclases, esta no representa una llamada
 * fallida a un proveedor — significa que nunca se alcanzó ningún proveedor porque ninguno está
 * configurado — así que deliberadamente no extiende esa jerarquía.
 */
public class AiProviderNotConfiguredException extends RuntimeException {

    public AiProviderNotConfiguredException(String message) {
        super(message);
    }
}
