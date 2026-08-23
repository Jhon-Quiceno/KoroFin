package com.korofin.backend.entity.ai;

import com.korofin.backend.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un registro de uso de un proveedor de IA, propiedad de un {@link User}.
 *
 * <p>Dos puntos de escritura llenan esta tabla:
 * <ul>
 *     <li>{@code AiUsageEventService#record} — una fila por llamada exitosa, escrita directamente
 *         por un llamador que no pasa por telemetría por intento. Siempre {@link #success}
 *         {@code true}, {@link #latencyMs}/{@link #errorType} {@code null}.</li>
 *     <li>{@code AiUsageEventService#recordAttempt} — una fila por INTENTO de proveedor dentro de
 *         una sola llamada de {@code AiChatOrchestrator#complete}/{@code completeVision}, incluidos
 *         los intentos que hicieron failover. {@link #success} distingue un intento fallido
 *         (latencia y {@link #errorType} completos, sin tokens/costo) del que finalmente
 *         respondió.</li>
 * </ul>
 *
 * <p>Como {@link AiMessage}, las filas son inmutables una vez creadas — no hay columna
 * {@code updated_at} — así que esta entidad solo tiene {@link #createdAt}. {@link #costEstimate}
 * es nulo cuando la combinación proveedor/modelo no tiene precio conocido todavía (ver
 * {@code AiProviderPricing}).
 */
@Entity
@Table(name = "ai_usage_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AiUsageEventType eventType;

    @Column(name = "tokens_used", nullable = false)
    private int tokensUsed;

    @Column(name = "cost_estimate", precision = 10, scale = 6)
    private BigDecimal costEstimate;

    /**
     * Cuánto tardó este intento puntual, en milisegundos, o {@code null} para filas escritas por
     * {@code AiUsageEventService#record} (que no lo mide).
     */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /**
     * Si este intento tuvo éxito. Siempre {@code true} para filas de {@code #record}; para filas
     * de {@code #recordAttempt}, {@code false} marca un proveedor que hizo failover al siguiente.
     */
    @Column(nullable = false)
    private boolean success = true;

    /**
     * Nombre simple de la clase de excepción del intento fallido (p. ej.
     * {@code "AiProviderTimeoutException"}), o {@code null} para un intento exitoso.
     */
    @Column(name = "error_type", length = 60)
    private String errorType;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
