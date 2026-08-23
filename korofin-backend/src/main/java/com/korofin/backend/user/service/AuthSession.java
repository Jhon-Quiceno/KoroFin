package com.korofin.backend.user.service;

import com.korofin.backend.user.dto.AuthResponse;

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
