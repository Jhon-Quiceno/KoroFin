package com.korofin.backend.common.security;

import com.korofin.backend.common.config.JwtProperties;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.exception.InvalidRefreshTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Genera y valida los JWT (HS256) de acceso y de refresh. La distinción entre ambos se hace con
 * el claim {@code token_type} ({@code access}/{@code refresh}); el refresh token además lleva un
 * {@code jti} (UUID) para poder rotarlo/revocarlo por fila en {@code refresh_tokens}.
 */
@Service
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_EMAIL = "email";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtProperties.accessExpirationMs());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_EMAIL, user.getEmail())
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(User user, UUID tokenId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtProperties.refreshExpirationMs());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(tokenId.toString())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parseToken(token);
        validateTokenType(claims, TOKEN_TYPE_ACCESS);
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseToken(token);
        validateTokenType(claims, TOKEN_TYPE_REFRESH);
        return claims;
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessExpirationMs() / 1000;
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidRefreshTokenException("Token inválido o expirado");
        }
    }

    private void validateTokenType(Claims claims, String expectedType) {
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(tokenType)) {
            throw new InvalidRefreshTokenException("Tipo de token inválido");
        }
    }
}
