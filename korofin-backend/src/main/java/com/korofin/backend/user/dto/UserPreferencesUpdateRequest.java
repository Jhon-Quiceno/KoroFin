package com.korofin.backend.user.dto;

import com.korofin.backend.user.entity.AppLanguage;
import com.korofin.backend.user.entity.ThemePreference;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Payload de {@code PATCH /api/users/preferences} — siempre se manda el objeto completo, no un
 * PATCH parcial campo por campo (mismo criterio que {@link ProfileUpdateRequest}).
 *
 * @param currency código ISO 4217 de 3 letras, acotado al set que hoy soporta la app (ver
 *                 {@code V1__create_users_and_refresh_tokens.sql})
 */
public record UserPreferencesUpdateRequest(
        @NotNull(message = "El tema es obligatorio")
        ThemePreference theme,
        @NotNull(message = "La moneda es obligatoria")
        @Pattern(regexp = "COP|USD|MXN|ARS|EUR", message = "Moneda no soportada")
        String currency,
        @NotNull(message = "El idioma es obligatorio")
        AppLanguage language
) {
}
