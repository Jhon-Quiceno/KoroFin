package com.korofin.backend.ai.dto;

/**
 * Estado de solo lectura de un proveedor de IA, expuesto por {@code GET /api/ai/providers/status}
 * — nunca lleva la API key del proveedor.
 *
 * @param name       el nombre de despliegue del proveedor (p. ej. {@code "nvidia"})
 * @param configured si el operador fijó una API key no vacía para este proveedor
 * @param priority   la posición 1-based de este proveedor entre los habilitados, o {@code null}
 *                   si no está habilitado
 */
public record AiProviderStatusResponse(String name, boolean configured, Integer priority) {
}
