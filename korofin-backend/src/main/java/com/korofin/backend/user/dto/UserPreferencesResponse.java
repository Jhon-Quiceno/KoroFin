package com.korofin.backend.user.dto;

import com.korofin.backend.user.entity.AppLanguage;
import com.korofin.backend.user.entity.ThemePreference;

/**
 * Respuesta de {@code GET /api/users/preferences} y {@code PATCH /api/users/preferences}.
 */
public record UserPreferencesResponse(
        ThemePreference theme,
        String currency,
        AppLanguage language
) {
}
