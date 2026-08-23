package com.korofin.backend.entity.integration;

import com.korofin.backend.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Vínculo entre un chat de Telegram y un {@link User} de KoroFin (dominio {@code integration},
 * fase 7 — ver docs/backend-plan.md sección 5).
 *
 * <p>{@link #telegramChatId} es único ({@code uk_telegram_links_chat_id}, ver
 * {@code V7__create_telegram_links.sql}): un chat de Telegram vincula a lo sumo un usuario a la
 * vez. Si ese chat ya estaba vinculado a otro usuario, {@code TelegramLinkService#confirmLink}
 * re-asigna la fila existente (actualiza {@link #user}) en vez de fallar por duplicado — el flujo
 * de vínculo no necesita "desvincular" explícitamente antes de vincular a otra cuenta.
 */
@Entity
@Table(name = "telegram_links")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private Long telegramChatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
