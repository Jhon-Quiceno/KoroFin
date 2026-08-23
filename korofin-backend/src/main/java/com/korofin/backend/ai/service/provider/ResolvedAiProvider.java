package com.korofin.backend.ai.service.provider;

/**
 * Un proveedor de IA completamente resuelto, listo para ser llamado por {@link AiChatClient}:
 * datos fijos del catálogo ({@code baseUrl}) combinados con credenciales configuradas por entorno
 * ({@code apiKey}, {@code model}).
 *
 * <p>Producido por {@link AiProviderRegistry#enabledInPriorityOrder()}.
 *
 * @param name        el nombre de despliegue del proveedor (ver {@link SupportedAiProvider#key()})
 * @param baseUrl     la URL base fija de la API del proveedor
 * @param apiKey      la API key cruda del proveedor, leída directamente del entorno
 * @param model       el identificador de modelo a pedir para llamadas de solo texto
 * @param visionModel el identificador de modelo a pedir para llamadas con imagen (ver
 *                    {@code AiChatOrchestrator#completeVision}), o {@code null} si este proveedor
 *                    no tiene un modelo de visión configurado/conocido — deliberadamente NO
 *                    reutilizado de {@code model}, ya que el modelo de texto normal de un
 *                    proveedor frecuentemente no tiene capacidad de visión en absoluto
 */
public record ResolvedAiProvider(String name, String baseUrl, String apiKey, String model, String visionModel) {
}
