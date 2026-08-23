package com.korofin.backend.entity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token emitido a un usuario. Se rota en cada uso ({@code RefreshTokenService#rotate}):
 * el token presentado se revoca y se crea uno nuevo, identificado por {@link #tokenId} (el claim
 * {@code jti} del JWT).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_id", nullable = false, unique = true)
    private UUID tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 120)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Si {@code true}, la sesión se considera "recordada" por el usuario (opt-in explícito al
     * hacer login) y se propaga al token rotado en cada refresh, para que la elección hecha al
     * loguearse sobreviva toda la cadena de rotaciones.
     */
    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;
}
