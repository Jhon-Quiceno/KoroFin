package com.korofin.backend.entity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Usuario de KoroFin. El correo es único (case-insensitive vía {@code UserRepository}), la
 * contraseña se guarda como hash BCrypt (fuerza 12, ver {@code SecurityConfig}).
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(exclude = "passwordHash")
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Cantidad de mensajes de chat de IA enviados por este usuario durante {@link #aiChatPeriod}.
     * Contador propio (en vez de contar filas de mensajes) para que sobreviva a la purga del
     * historial de chat en cada login (ver {@code UserService#login}) y pueda reservarse
     * atómicamente. El dominio {@code ai} todavía no existe (llega en una fase posterior); esta
     * columna queda lista desde ya para no requerir otra migración cuando se implemente.
     */
    @Column(name = "ai_chat_used", nullable = false)
    private int aiChatUsed = 0;

    /**
     * Mes calendario UTC al que pertenece {@link #aiChatUsed}, formato {@code "YYYY-MM"}.
     * {@code null} hasta el primer mensaje de chat de IA del usuario.
     */
    @Column(name = "ai_chat_period", length = 7)
    private String aiChatPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    private ThemePreference theme = ThemePreference.SYSTEM;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "COP";

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 5)
    private AppLanguage language = AppLanguage.ES;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
