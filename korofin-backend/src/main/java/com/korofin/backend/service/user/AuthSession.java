package com.korofin.backend.service.user;

import com.korofin.backend.dto.user.AuthResponse;

/**
 * Resultado interno de una operación de autenticación (registro/login/refresh), antes de
 * ensamblarse en la respuesta HTTP final del controller.
 */
public record AuthSession(
        AuthResponse response,
        String refreshToken,
        boolean rememberMe
) {
}
