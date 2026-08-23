package com.korofin.backend.repository.ai;

import com.korofin.backend.entity.ai.AiUsageEvent;
import com.korofin.backend.entity.ai.AiUsageEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Acceso a persistencia de {@link AiUsageEvent}, siempre delimitado por dueño.
 */
public interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, Long> {

    List<AiUsageEvent> findAllByUser_IdAndCreatedAtBetween(Long userId, Instant start, Instant end);

    /**
     * Consumo de tokens agregado por {@link AiUsageEventType} para un usuario dentro de
     * {@code [start, end)}, usado por {@code AiUsageEventService#getUsageSummary}.
     *
     * <p>Filtra a filas {@code success = true} exclusivamente: dado que
     * {@code AiUsageEventService#recordAttempt} también persiste una fila por cada intento de
     * proveedor que hizo failover (ver el Javadoc de {@link AiUsageEvent}), contarlas acá inflaría
     * el resumen de "llamadas de IA este mes" con intentos que el usuario nunca vio fallar — solo
     * el intento que finalmente respondió (o una fila de {@code record()}, siempre
     * {@code success = true}) debe contar.
     */
    @Query("""
            SELECT e.eventType AS eventType, COUNT(e) AS eventCount, COALESCE(SUM(e.tokensUsed), 0) AS totalTokens
            FROM AiUsageEvent e
            WHERE e.user.id = :userId AND e.createdAt >= :start AND e.createdAt < :end AND e.success = true
            GROUP BY e.eventType
            """)
    List<AiUsageEventTypeAggregate> aggregateByEventType(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    /** Proyección que respalda {@link #aggregateByEventType}. */
    interface AiUsageEventTypeAggregate {
        AiUsageEventType getEventType();

        long getEventCount();

        long getTotalTokens();
    }
}
