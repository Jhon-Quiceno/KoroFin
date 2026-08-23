package com.korofin.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de firma/expiración de JWT (prefijo {@code app.jwt}). A diferencia de FinSmart, no
 * incluye las propiedades de cookie ({@code refreshCookieName/Secure/SameSite}): KoroFin es
 * mobile-only y el refresh token nunca viaja en cookie (ver plan-backend.md sección 3.2).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long accessExpirationMs,
        long refreshExpirationMs
) {
}
