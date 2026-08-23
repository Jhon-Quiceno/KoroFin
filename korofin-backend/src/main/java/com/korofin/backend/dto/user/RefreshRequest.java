package com.korofin.backend.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo JSON de {@code POST /api/users/refresh} y {@code POST /api/users/logout}. KoroFin es
 * mobile-only: el refresh token siempre viaja acá, nunca en una cookie (a diferencia de FinSmart,
 * que soportaba ambos caminos).
 */
public record RefreshRequest(
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken
) {
}
