package com.korofin.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El correo electrónico no es válido")
        @Size(max = 180, message = "El correo electrónico no puede superar 180 caracteres")
        String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, max = 100, message = "La contraseña debe tener entre 6 y 100 caracteres")
        String password,
        /**
         * Nullable a propósito (clientes que no manden el campo mantienen el default más seguro,
         * {@code false}). Se propaga hasta {@code RefreshToken#rememberMe} — hoy no cambia el
         * comportamiento del token (KoroFin es mobile-only, sin cookie de sesión/persistente que
         * distinguir), pero queda disponible como base para una futura pantalla de "dispositivos
         * conectados" (ver plan-backend.md sección 3.2).
         */
        Boolean rememberMe
) {
}
