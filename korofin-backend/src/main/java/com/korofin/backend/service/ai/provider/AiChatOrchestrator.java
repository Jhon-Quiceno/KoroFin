package com.korofin.backend.service.ai.provider;

import com.korofin.backend.exception.ai.AiProviderException;
import com.korofin.backend.exception.ai.AiProviderNotConfiguredException;
import com.korofin.backend.exception.ai.AiProvidersExhaustedException;
import com.korofin.backend.service.ai.AiUsageEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Failover transparente entre todos los proveedores de IA configurados, en orden de prioridad.
 *
 * <p>Toda llamada de {@code AiChatService}/{@code AiInsightService}/{@code AiCategorizationService}
 * pasa por {@link #complete(List, AiCallContext)} en vez de resolver un único proveedor y llamar a
 * {@link AiChatClient} directamente: si el proveedor probado falla por cualquier motivo
 * clasificado por {@link AiProviderException} (autenticación, límite de uso, timeout, modelo
 * faltante, indisponibilidad general), se prueba automáticamente el siguiente proveedor
 * configurado, sin diferencia visible para el usuario final. Solo cuando todos los proveedores
 * configurados fallaron — o ninguno está configurado — el llamador ve una excepción terminal y
 * genérica (ver {@link #GENERIC_MESSAGE}); qué proveedor falló o por qué nunca se filtra al
 * llamador.
 *
 * <p><b>Telemetría por intento:</b> cuando un llamador provee un {@link AiCallContext} no nulo,
 * cada intento dentro del ciclo de failover —tanto los que hacen failover como el que finalmente
 * responde— se registra vía {@link AiUsageEventService#recordAttempt}, con latencia, éxito/fracaso
 * y (para fallos) el nombre simple de la excepción lanzada. Esto solo puede pasar acá, dentro del
 * ciclo mismo: un llamador solo ve el resultado ganador final (o la excepción terminal), sin
 * visibilidad de cuántos proveedores se probaron antes ni cuánto tardó cada uno.
 *
 * <p><b>Prioridad de proveedor por tarea:</b> {@link #complete(List, AiCallContext)} también usa
 * {@code ctx}, cuando está presente, para resolver proveedores vía
 * {@link AiProviderRegistry#enabledInPriorityOrder(com.korofin.backend.entity.ai.AiUsageEventType)}
 * en vez del orden global {@link AiProviderRegistry#enabledInPriorityOrder()} — esto deja que un
 * operador configure un orden de intento distinto por operación asistida por IA (chat,
 * categorización, insights, ...) vía {@code app.ai.task-priority.<task>}.
 * {@link #completeVision(List, AiCallContext)} deliberadamente NO hace esto — ver el Javadoc de
 * ese método.
 */
@Service
public class AiChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiChatOrchestrator.class);

    /**
     * Mensaje neutral y no accionable mostrado al usuario final siempre que el asistente no pudo
     * producir una respuesta — reutilizado tal cual por {@code GlobalExceptionHandler} para que
     * este texto exacto esté definido en exactamente un lugar.
     */
    public static final String GENERIC_MESSAGE =
            "El asistente no está disponible en este momento. Inténtalo de nuevo más tarde.";

    private final AiProviderRegistry registry;
    private final AiChatClient aiChatClient;
    private final AiUsageEventService aiUsageEventService;
    private final AiProviderPricing aiProviderPricing;

    public AiChatOrchestrator(
            AiProviderRegistry registry,
            AiChatClient aiChatClient,
            AiUsageEventService aiUsageEventService,
            AiProviderPricing aiProviderPricing
    ) {
        this.registry = registry;
        this.aiChatClient = aiChatClient;
        this.aiUsageEventService = aiUsageEventService;
        this.aiProviderPricing = aiProviderPricing;
    }

    /**
     * Igual que {@link #complete(List, AiCallContext)} sin registrar telemetría — ver el Javadoc
     * de la clase.
     */
    public ChatCompletionResult complete(List<ChatMessage> messages) {
        return complete(messages, null);
    }

    /**
     * Envía {@code messages} al primer proveedor configurado que responda con éxito, probando
     * todos los proveedores configurados en orden de prioridad antes de rendirse.
     *
     * @param messages la conversación completa a enviar, en orden (prompt de sistema primero)
     * @param ctx      atribución para telemetría por intento (ver el Javadoc de la clase), o
     *                 {@code null} para no registrar telemetría; cuando no es nulo, su
     *                 {@code eventType} también decide el orden de proveedor
     * @return la respuesta del proveedor exitoso
     * @throws AiProviderNotConfiguredException si no hay ningún proveedor configurado
     * @throws AiProvidersExhaustedException    si todos los proveedores configurados fallaron
     */
    public ChatCompletionResult complete(List<ChatMessage> messages, AiCallContext ctx) {
        List<ResolvedAiProvider> providers = ctx != null
                ? registry.enabledInPriorityOrder(ctx.eventType())
                : registry.enabledInPriorityOrder();
        if (providers.isEmpty()) {
            throw new AiProviderNotConfiguredException(GENERIC_MESSAGE);
        }

        for (ResolvedAiProvider provider : providers) {
            long startNanos = System.nanoTime();
            try {
                ChatCompletionResult result = aiChatClient.complete(provider, messages);
                int latencyMs = elapsedMs(startNanos);
                ChatCompletionResult stamped = result.withProvider(provider.name(), provider.model());
                recordAttemptIfPresent(ctx, provider, stamped, latencyMs, true, null);
                return stamped;
            } catch (AiProviderException ex) {
                int latencyMs = elapsedMs(startNanos);
                log.warn("AI provider {} failed ({}); trying next configured provider",
                        provider.name(), ex.getClass().getSimpleName());
                recordAttemptIfPresent(ctx, provider, null, latencyMs, false, ex.getClass().getSimpleName());
            }
        }

        throw new AiProvidersExhaustedException(GENERIC_MESSAGE);
    }

    /**
     * Igual que {@link #completeVision(List, AiCallContext)} sin registrar telemetría — ver el
     * Javadoc de la clase.
     */
    public ChatCompletionResult completeVision(List<ChatMessage> messages) {
        return completeVision(messages, null);
    }

    /**
     * Envía un turno de visión ({@code messages} construido con {@link ChatMessage#userWithImage})
     * al primer proveedor configurado y con capacidad de visión que responda con éxito, probando
     * todos los proveedores configurados con un {@link ResolvedAiProvider#visionModel()} no vacío
     * en orden de prioridad antes de rendirse — el mismo failover transparente que
     * {@link #complete(List, AiCallContext)}, restringido al subconjunto de proveedores
     * configurados que el catálogo de este proyecto sabe que tienen un modelo con capacidad de
     * visión (ver {@link SupportedAiProvider#defaultVisionModel()}).
     *
     * <p>Los proveedores configurados sin un modelo de visión conocido (un
     * {@link ResolvedAiProvider#visionModel()} nulo/vacío) se saltan por completo: nunca se
     * intentan, ya que un proveedor OpenAI-compatible al que se le da un {@code image_url}
     * inesperado puede rechazar el request de plano o, peor, ignorarlo en silencio y alucinar una
     * respuesta en vez de leer realmente la imagen.
     *
     * <p>Sin importar qué proveedor se pruebe, este método siempre sustituye
     * {@link ResolvedAiProvider#visionModel()} como el modelo a pedir — el modelo de texto normal
     * de un proveedor frecuentemente NO tiene capacidad de visión en absoluto, así que el modelo
     * de texto nunca se reutiliza para un turno de visión.
     *
     * @param messages la conversación completa a enviar, en orden (prompt de sistema primero),
     *                 incluyendo exactamente un turno de visión construido con
     *                 {@link ChatMessage#userWithImage}
     * @param ctx      atribución para telemetría por intento (ver el Javadoc de la clase), o
     *                 {@code null} para no registrar telemetría
     * @return la respuesta del proveedor exitoso
     * @throws AiProviderNotConfiguredException si ningún proveedor habilitado tiene un modelo de
     *                                           visión configurado
     * @throws AiProvidersExhaustedException    si todos los proveedores con capacidad de visión
     *                                           configurados fallaron
     */
    public ChatCompletionResult completeVision(List<ChatMessage> messages, AiCallContext ctx) {
        List<ResolvedAiProvider> visionProviders = registry.enabledInPriorityOrder().stream()
                .filter(provider -> provider.visionModel() != null && !provider.visionModel().isBlank())
                .toList();
        if (visionProviders.isEmpty()) {
            throw new AiProviderNotConfiguredException(GENERIC_MESSAGE);
        }

        for (ResolvedAiProvider provider : visionProviders) {
            // El modelo de texto normal (provider.model()) frecuentemente NO tiene capacidad de
            // visión - se sustituye el modelo específico de visión antes de llamar, nunca se
            // reutiliza el de texto para un turno de imagen.
            ResolvedAiProvider visionProvider = new ResolvedAiProvider(
                    provider.name(), provider.baseUrl(), provider.apiKey(), provider.visionModel(), provider.visionModel()
            );
            long startNanos = System.nanoTime();
            try {
                ChatCompletionResult result = aiChatClient.complete(visionProvider, messages);
                int latencyMs = elapsedMs(startNanos);
                ChatCompletionResult stamped = result.withProvider(visionProvider.name(), visionProvider.model());
                recordAttemptIfPresent(ctx, visionProvider, stamped, latencyMs, true, null);
                return stamped;
            } catch (AiProviderException ex) {
                int latencyMs = elapsedMs(startNanos);
                log.warn("AI provider {} failed on a vision turn ({}); trying next vision-capable configured provider",
                        visionProvider.name(), ex.getClass().getSimpleName());
                recordAttemptIfPresent(ctx, visionProvider, null, latencyMs, false, ex.getClass().getSimpleName());
            }
        }

        throw new AiProvidersExhaustedException(GENERIC_MESSAGE);
    }

    /** Registra la telemetría de un intento vía {@link AiUsageEventService#recordAttempt}, o no hace nada si {@code ctx == null}. */
    private void recordAttemptIfPresent(
            AiCallContext ctx, ResolvedAiProvider provider, ChatCompletionResult result, int latencyMs, boolean success, String errorType
    ) {
        if (ctx == null) {
            return;
        }
        int tokensUsed = result != null ? totalTokens(result) : 0;
        BigDecimal costEstimate = success ? aiProviderPricing.estimateCost(provider.name(), provider.model(), tokensUsed) : null;
        aiUsageEventService.recordAttempt(
                ctx.userId(), provider.name(), ctx.eventType(), tokensUsed, costEstimate, latencyMs, success, errorType
        );
    }

    /** Suma {@code promptTokens + completionTokens}, tratando cualquiera como {@code 0} si el proveedor no lo reportó. */
    private static int totalTokens(ChatCompletionResult result) {
        int prompt = result.promptTokens() != null ? result.promptTokens() : 0;
        int completion = result.completionTokens() != null ? result.completionTokens() : 0;
        return prompt + completion;
    }

    /** Milisegundos transcurridos desde {@code startNanos} (ver {@link System#nanoTime()}), redondeado hacia abajo. */
    private static int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }
}
