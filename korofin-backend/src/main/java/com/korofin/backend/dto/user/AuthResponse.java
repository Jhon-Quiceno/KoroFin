package com.korofin.backend.dto.user;

/**
 * Respuesta de {@code /register}, {@code /login} y {@code /refresh}. KoroFin es mobile-only puro:
 * a diferencia de FinSmart, no hay rama condicional por header ni cookie — {@code refreshToken}
 * siempre viaja en el body. El cliente Flutter lo guarda en almacenamiento seguro del device
 * ({@code flutter_secure_storage}).
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        String refreshToken
) {
}
