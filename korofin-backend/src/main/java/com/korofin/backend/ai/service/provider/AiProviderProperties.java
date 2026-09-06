package com.korofin.backend.ai.service.provider;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Enlaza {@code app.ai.*} desde el entorno: qué proveedores están configurados (por API key) y en
 * qué orden de prioridad el asistente debe probarlos (ver {@code docs/backend-plan.md} sección 4).
 *
 * <p>Declarada como clase mutable simple (Lombok {@link Getter}/{@link Setter}) en vez de un
 * record, porque el binding JavaBeans de {@code @ConfigurationProperties} de Spring necesita
 * setters en el tipo raíz enlazado; el {@link ProviderCredentials} anidado sí puede ser un record
 * inmutable, porque Spring soporta constructor binding para tipos
 * {@code @ConfigurationProperties} anidados.
 *
 * @see AiProviderRegistry
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {

    /**
     * Claves de proveedor (ver {@link SupportedAiProvider#key()}) en el orden en que deben
     * probarse, p. ej. {@code ["gemini", "nvidia", "opencode"]}. Entradas desconocidas, vacías o
     * duplicadas son ignoradas por {@link AiProviderRegistry}.
     */
    private List<String> priority = List.of();

    /**
     * Credenciales por proveedor, indexadas por {@link SupportedAiProvider#key()} (p. ej.
     * {@code "nvidia"}).
     */
    private Map<String, ProviderCredentials> providers = Map.of();

    /**
     * Overrides de prioridad de proveedor por tarea, indexados por el nombre en minúscula de una
     * constante de {@code AiUsageEventType} (p. ej. {@code "chat"}, {@code "categorize"},
     * {@code "insight"}, {@code "statement_extract"}). Mismo formato que {@link #priority}: una
     * lista de claves de proveedor (ver {@link SupportedAiProvider#key()}), leída como un orden
     * preferido de intento, nunca como una lista de permitidos.
     *
     * <p>Una clave ausente, o mapeada a una lista vacía (después de que
     * {@link AiProviderRegistry} descarte entradas desconocidas/vacías), simplemente hace que esa
     * tarea caiga al orden global de {@link #priority} — este mapa solo deja al operador aislar
     * una tarea específica (p. ej. insights financieros) para probar un orden de proveedor
     * distinto, sin cambiar el orden que usa cualquier otra tarea.
     */
    private Map<String, List<String>> taskPriority = Map.of();

    /**
     * Cantidad máxima de mensajes de chat de IA de rol USER que un solo usuario puede enviar por
     * mes calendario UTC, reforzada por {@code AiChatService#chat} antes de contactar a cualquier
     * proveedor.
     */
    private int monthlyMessageLimit = 5;

    /**
     * @param apiKey      la API key cruda del proveedor, o vacía/{@code null} si no está
     *                    configurado
     * @param model       el identificador de modelo a usar, o vacío/{@code null} para caer al
     *                    {@link SupportedAiProvider#defaultModel()}
     * @param visionModel el identificador de modelo a usar para llamadas con imagen (ver
     *                    {@code AiChatOrchestrator#completeVision}), o vacío/{@code null} para
     *                    caer al {@link SupportedAiProvider#defaultVisionModel()}. Solo relevante
     *                    para proveedores que soportan visión de verdad (hoy: NVIDIA, Gemini y el
     *                    fallback Nemotron de OpenRouter) — el {@code model} normal de un
     *                    proveedor frecuentemente NO tiene capacidad de visión, así que las
     *                    llamadas de visión nunca deben reutilizarlo a ciegas.
     */
    public record ProviderCredentials(String apiKey, String model, String visionModel) {
    }
}
