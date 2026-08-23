package com.korofin.backend.security;

import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.user.InvalidRefreshTokenException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "una-clave-secreta-de-prueba-suficientemente-larga-para-hmac-sha256",
                "korofin-backend",
                900_000L,
                604_800_000L
        );
        jwtService = new JwtService(properties);

        user = new User();
        user.setId(42L);
        user.setEmail("ana@korofin.dev");
    }

    @Test
    void generateAccessTokenCanBeParsedBackWithExpectedClaims() {
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("token_type", String.class)).isEqualTo("access");
        assertThat(claims.get("email", String.class)).isEqualTo("ana@korofin.dev");
    }

    @Test
    void generateRefreshTokenCanBeParsedBackWithExpectedClaims() {
        UUID tokenId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(user, tokenId);
        Claims claims = jwtService.parseRefreshToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("token_type", String.class)).isEqualTo("refresh");
        assertThat(claims.getId()).isEqualTo(tokenId.toString());
    }

    @Test
    void parseAccessTokenRejectsARefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(user, UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void parseRefreshTokenRejectsAnAccessToken() {
        String accessToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void parseTokenRejectsGarbage() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("no-soy-un-jwt"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void getAccessTokenExpirationSecondsConvertsMillisToSeconds() {
        assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(900L);
    }
}
