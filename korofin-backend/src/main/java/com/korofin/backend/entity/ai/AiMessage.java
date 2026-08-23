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

import java.time.Instant;

/**
 * Un turno de conversación de IA o un insight financiero generado, propiedad de un {@link User}.
 *
 * <p>A diferencia de la mayoría de entidades del proyecto, las filas son inmutables una vez
 * creadas — no hay columna {@code updated_at} (ver {@code V4__create_ai_domain.sql}) — así que
 * esta entidad solo tiene {@link #createdAt}. {@link #providerName} y {@link #model} registran
 * qué proveedor/modelo de IA produjo una respuesta {@link AiMessageRole#ASSISTANT}; ambos son
 * {@code null} para filas {@link AiMessageRole#USER}.
 */
@Entity
@Table(name = "ai_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AiMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AiMessageKind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "provider_name", length = 60)
    private String providerName;

    @Column(length = 120)
    private String model;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
