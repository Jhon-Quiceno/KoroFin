package com.korofin.backend.ai.service.provider;

import com.korofin.backend.ai.entity.AiUsageEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resuelve qué proveedores de IA están habilitados (configurados con una API key no vacía) y en
 * qué orden el asistente debe probarlos, a partir de {@link AiProviderProperties}.
 *
 * @see AiChatOrchestrator
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    /**
     * Orden usado siempre que {@code app.ai.priority} está sin fijar o, tras descartar entradas
     * desconocidas/vacías/duplicadas, queda vacío: Gemini -> NVIDIA -> OpenCode -> OpenRouter ->
     * Groq (ver {@code docs/backend-plan.md} sección 4). Groq va al final: no tiene una API key
     * real configurada todavía (ver {@link SupportedAiProvider#GROQ}), así que se queda inerte
     * hasta que el operador fije {@code GROQ_API_KEY} — pero DEBE estar listado acá, o
     * {@link #resolvePriorityOrder()} nunca lo probaría ni siquiera con una key configurada,
     * porque esta lista es también a la que cae un {@code app.ai.priority} vacío/sin fijar.
     */
    private static final List<SupportedAiProvider> DEFAULT_PRIORITY = List.of(
            SupportedAiProvider.GEMINI,
            SupportedAiProvider.NVIDIA,
            SupportedAiProvider.OPENCODE,
            SupportedAiProvider.OPENROUTER,
            SupportedAiProvider.GROQ
    );

    private final AiProviderProperties properties;

    public AiProviderRegistry(AiProviderProperties properties) {
        this.properties = properties;
    }

    /**
     * @return cada proveedor configurado (API key no vacía), resuelto con su modelo (configurado
     *         o el default del catálogo) y ordenado según {@code app.ai.priority} (cayendo al
     *         orden por defecto confirmado cuando está sin fijar o vacío tras el filtrado)
     */
    public List<ResolvedAiProvider> enabledInPriorityOrder() {
        return resolveEnabled(resolvePriorityOrder());
    }

    /**
     * Igual que {@link #enabledInPriorityOrder()}, pero deja que
     * {@code app.ai.task-priority.<task>} (ver {@link AiProviderProperties#getTaskPriority()})
     * sobreescriba el orden de intento solo para una operación asistida por IA específica, sin
     * cambiar el orden que usa cualquier otra tarea.
     *
     * <p>Cuando no hay override configurado para {@code taskType} — la clave está ausente, o
     * mapea a una lista que queda vacía tras descartar entradas desconocidas/vacías (mismo
     * filtrado que {@link #resolvePriorityOrder()} ya aplica a {@code app.ai.priority}) — esto
     * delega directo en {@link #enabledInPriorityOrder()}.
     *
     * @param taskType para qué operación asistida por IA es esta llamada (ver
     *                 {@link AiUsageEventType})
     * @return los proveedores habilitados, en el orden con override de la tarea si hay uno
     *         configurado, o el orden global en otro caso
     */
    public List<ResolvedAiProvider> enabledInPriorityOrder(AiUsageEventType taskType) {
        List<String> taskOverride = properties.getTaskPriority().get(taskType.name().toLowerCase(Locale.ROOT));
        List<SupportedAiProvider> parsedOverride = parseConfiguredOrder(taskOverride);
        if (parsedOverride == null) {
            return enabledInPriorityOrder();
        }
        return resolveEnabled(parsedOverride);
    }

    /**
     * Paso de resolución compartido por {@link #enabledInPriorityOrder()} y
     * {@link #enabledInPriorityOrder(AiUsageEventType)}: filtra {@code order} a los proveedores
     * con una API key configurada no vacía, resolviendo el modelo de cada uno (configurado o
     * default del catálogo).
     */
    private List<ResolvedAiProvider> resolveEnabled(List<SupportedAiProvider> order) {
        List<ResolvedAiProvider> resolved = new ArrayList<>();
        for (SupportedAiProvider provider : order) {
            AiProviderProperties.ProviderCredentials credentials = properties.getProviders().get(provider.key());
            if (credentials == null || credentials.apiKey() == null || credentials.apiKey().isBlank()) {
                continue;
            }
            String model = credentials.model() != null && !credentials.model().isBlank()
                    ? credentials.model()
                    : provider.defaultModel();
            String visionModel = credentials.visionModel() != null && !credentials.visionModel().isBlank()
                    ? credentials.visionModel()
                    : provider.defaultVisionModel();
            resolved.add(new ResolvedAiProvider(provider.key(), provider.baseUrl(), credentials.apiKey(), model, visionModel));
        }
        return resolved;
    }

    /**
     * @return el estado de todos los proveedores conocidos, en el orden de declaración del
     *         catálogo, sin exponer nunca la API key
     */
    public List<AiProviderStatus> status() {
        List<String> enabledNamesInOrder = enabledInPriorityOrder().stream().map(ResolvedAiProvider::name).toList();
        List<AiProviderStatus> statuses = new ArrayList<>();
        for (SupportedAiProvider provider : SupportedAiProvider.values()) {
            int index = enabledNamesInOrder.indexOf(provider.key());
            boolean configured = index >= 0;
            Integer priority = configured ? index + 1 : null;
            statuses.add(new AiProviderStatus(provider.key(), configured, priority));
        }
        return statuses;
    }

    /**
     * Loguea, una vez al arrancar, qué proveedores de IA (si alguno) están habilitados — el
     * asistente no es central al producto, así que la aplicación siempre arranca sin importar el
     * resultado.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logStartupStatus() {
        List<ResolvedAiProvider> enabled = enabledInPriorityOrder();
        if (enabled.isEmpty()) {
            log.warn("No AI provider is configured — the assistant will be unavailable until at least one "
                    + "<PROVIDER>_API_KEY is set");
            return;
        }
        String summary = enabled.stream()
                .map(provider -> provider.name() + " (" + provider.model() + ")")
                .reduce((a, b) -> a + " -> " + b)
                .orElse("");
        log.info("AI providers enabled in priority order: {}", summary);
    }

    /**
     * {@code app.ai.priority} solo expresa un orden de intento preferido — nunca es una lista de
     * permitidos. Cualquier proveedor del catálogo no nombrado explícitamente (p. ej. el operador
     * fijó {@code AI_PROVIDER_PRIORITY=nvidia} pero también configuró un {@code OPENROUTER_API_KEY}
     * válido) igual se agrega al final, en el orden de {@link #DEFAULT_PRIORITY}, así que un
     * proveedor configurado-pero-no-listado siempre se prueba antes de rendirse.
     */
    private List<SupportedAiProvider> resolvePriorityOrder() {
        List<SupportedAiProvider> parsed = parseConfiguredOrder(properties.getPriority());
        return parsed != null ? parsed : DEFAULT_PRIORITY;
    }

    /**
     * Generalización de la regla de parseo de {@code app.ai.priority} de arriba, reutilizada por
     * {@link #enabledInPriorityOrder(AiUsageEventType)} para
     * {@code app.ai.task-priority.<task>}: descarta entradas desconocidas/vacías/duplicadas de
     * {@code configuredKeys} (conservando la primera ocurrencia de cada una), y luego agrega
     * cualquier proveedor de {@link #DEFAULT_PRIORITY} que no esté listado todavía.
     *
     * @param configuredKeys claves de proveedor crudas en el orden configurado, o
     *                       {@code null}/vacío
     * @return el orden resuelto, o {@code null} cuando {@code configuredKeys} es nulo/vacío o
     *         cada entrada era desconocida/vacía (es decir, no se configuró nada usable)
     */
    private List<SupportedAiProvider> parseConfiguredOrder(List<String> configuredKeys) {
        if (configuredKeys == null || configuredKeys.isEmpty()) {
            return null;
        }

        List<SupportedAiProvider> ordered = new ArrayList<>();
        Set<SupportedAiProvider> seen = new LinkedHashSet<>();
        for (String rawKey : configuredKeys) {
            SupportedAiProvider.fromKey(rawKey).ifPresent(provider -> {
                if (seen.add(provider)) {
                    ordered.add(provider);
                }
            });
        }
        if (ordered.isEmpty()) {
            return null;
        }
        for (SupportedAiProvider provider : DEFAULT_PRIORITY) {
            if (seen.add(provider)) {
                ordered.add(provider);
            }
        }
        return ordered;
    }

    /**
     * Instantánea de solo lectura del estado de configuración de un proveedor, segura de exponer
     * en {@code GET /api/ai/providers/status} — nunca lleva la API key.
     *
     * @param name       el nombre de despliegue del proveedor (ver {@link SupportedAiProvider#key()})
     * @param configured si el operador fijó una API key no vacía para este proveedor
     * @param priority   la posición 1-based de este proveedor entre los habilitados, o
     *                   {@code null} si no está habilitado
     */
    public record AiProviderStatus(String name, boolean configured, Integer priority) {
    }
}
