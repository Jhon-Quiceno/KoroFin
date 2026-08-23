package com.korofin.backend.service.ai;

import com.korofin.backend.dto.ai.AiUsageEventSummaryResponse;
import com.korofin.backend.entity.ai.AiUsageEvent;
import com.korofin.backend.entity.ai.AiUsageEventType;
import com.korofin.backend.repository.ai.AiUsageEventRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

/**
 * Escribe y resume filas de {@link AiUsageEvent} — el rastreo operacional de uso de proveedores
 * de IA, independiente de la cuota mensual de chat de cara al usuario reforzada por
 * {@code AiChatService#reserveMonthlyQuota}.
 *
 * <p>{@link #record} lo llama directamente un futuro llamador que no pasa por telemetría por
 * intento (p. ej. una extracción de extracto bancario de una fase posterior). Cada otra
 * funcionalidad de IA de este dominio ({@code AiChatService}, {@code AiCategorizationService},
 * {@code AiInsightService}, {@code FinancialSummaryQueryService}, {@code ReceiptExtractionService})
 * en cambio pasa un {@code AiCallContext} a
 * {@code AiChatOrchestrator#complete}/{@code completeVision}, que llama a {@link #recordAttempt}
 * por su cuenta para cada intento en su ciclo de failover. Ambos métodos deliberadamente se tragan
 * cualquier falla de persistencia: un evento de rastreo que falla al guardarse nunca debe
 * convertir una respuesta de IA por lo demás exitosa en un error para el usuario final.
 */
@Service
public class AiUsageEventService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageEventService.class);

    private final AiUsageEventRepository usageEventRepository;
    private final UserRepository userRepository;

    public AiUsageEventService(AiUsageEventRepository usageEventRepository, UserRepository userRepository) {
        this.usageEventRepository = usageEventRepository;
        this.userRepository = userRepository;
    }

    /**
     * Persiste una fila de {@link AiUsageEvent} para {@code userId}. Nunca lanza: cualquier falla
     * (p. ej. un problema transitorio de base de datos) se loguea en {@code WARN} y se traga, ya
     * que el rastreo es best-effort y no debe bloquear ni hacer fallar la respuesta de IA ya
     * devuelta al llamador.
     *
     * @param userId       el usuario en cuyo nombre se hizo la llamada de IA
     * @param provider     el proveedor que finalmente respondió (ver {@code ChatCompletionResult#providerName})
     * @param eventType    qué operación asistida por IA produjo este evento
     * @param tokensUsed   tokens totales consumidos (prompt + respuesta); {@code 0} cuando el
     *                     proveedor no reportó consumo
     * @param costEstimate costo estimado de la llamada, o {@code null} cuando no hay datos de
     *                     precio todavía
     */
    @Transactional
    public void record(Long userId, String provider, AiUsageEventType eventType, int tokensUsed, BigDecimal costEstimate) {
        try {
            AiUsageEvent event = new AiUsageEvent();
            event.setUser(userRepository.getReferenceById(userId));
            event.setProvider(provider);
            event.setEventType(eventType);
            event.setTokensUsed(Math.max(tokensUsed, 0));
            event.setCostEstimate(costEstimate);
            usageEventRepository.save(event);
        } catch (RuntimeException ex) {
            log.warn("ai_usage_event_tracking_failed userId={} provider={} eventType={}", userId, provider, eventType, ex);
        }
    }

    /**
     * Persiste una fila de {@link AiUsageEvent} para un único INTENTO de proveedor dentro de
     * {@code AiChatOrchestrator#complete}/{@code completeVision} — se llama para cada proveedor
     * probado en el ciclo de failover, tanto los que hicieron failover como el que finalmente
     * respondió. Nunca lanza, mismo contrato best-effort que {@link #record}.
     *
     * <p>Corre en {@link Propagation#REQUIRES_NEW} en vez de unirse a la transacción del
     * llamador: {@code AiChatOrchestrator#complete} típicamente se invoca desde dentro de un
     * método {@code @Transactional} más amplio (p. ej. {@code AiChatService#chat}), y una falla de
     * INSERT de telemetría nunca debe marcar esa transacción externa como rollback-only.
     *
     * @param userId       el usuario en cuyo nombre se hizo la llamada de IA
     * @param provider     el proveedor con el que se hizo este intento específico
     * @param eventType    qué operación asistida por IA produjo este evento
     * @param tokensUsed   tokens totales consumidos por este intento; {@code 0} para un intento
     *                     fallido o cuando el proveedor no reportó consumo
     * @param costEstimate costo estimado de este intento, o {@code null} para un intento fallido
     *                     o sin datos de precio todavía
     * @param latencyMs    cuánto tardó este intento, en milisegundos
     * @param success      si este intento tuvo éxito
     * @param errorType    nombre simple de la clase de excepción del intento fallido, o
     *                     {@code null} para un intento exitoso
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(
            Long userId,
            String provider,
            AiUsageEventType eventType,
            int tokensUsed,
            BigDecimal costEstimate,
            Integer latencyMs,
            boolean success,
            String errorType
    ) {
        try {
            AiUsageEvent event = new AiUsageEvent();
            event.setUser(userRepository.getReferenceById(userId));
            event.setProvider(provider);
            event.setEventType(eventType);
            event.setTokensUsed(Math.max(tokensUsed, 0));
            event.setCostEstimate(costEstimate);
            event.setLatencyMs(latencyMs);
            event.setSuccess(success);
            event.setErrorType(errorType);
            usageEventRepository.save(event);
        } catch (RuntimeException ex) {
            log.warn("ai_usage_event_attempt_tracking_failed userId={} provider={} eventType={} success={}",
                    userId, provider, eventType, success, ex);
        }
    }

    /**
     * Resume el uso de IA rastreado del usuario autenticado actual para el mes calendario UTC
     * dado.
     */
    @Transactional(readOnly = true)
    public AiUsageEventSummaryResponse getUsageSummary(YearMonth period) {
        return getUsageSummary(SecurityUtils.getCurrentUserId(), period);
    }

    /**
     * Resume el uso de IA rastreado de {@code userId} para el mes calendario UTC dado.
     */
    @Transactional(readOnly = true)
    public AiUsageEventSummaryResponse getUsageSummary(Long userId, YearMonth period) {
        Instant start = period.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<AiUsageEventType, Long> eventsByType = new EnumMap<>(AiUsageEventType.class);
        long totalTokens = 0;
        long totalEvents = 0;
        for (AiUsageEventRepository.AiUsageEventTypeAggregate aggregate
                : usageEventRepository.aggregateByEventType(userId, start, end)) {
            eventsByType.put(aggregate.getEventType(), aggregate.getEventCount());
            totalTokens += aggregate.getTotalTokens();
            totalEvents += aggregate.getEventCount();
        }

        return new AiUsageEventSummaryResponse(period, totalTokens, totalEvents, eventsByType);
    }
}
