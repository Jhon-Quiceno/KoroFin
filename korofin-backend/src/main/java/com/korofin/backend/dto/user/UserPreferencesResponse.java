package com.korofin.backend.dto.user;

import com.korofin.backend.entity.user.AppLanguage;
import com.korofin.backend.entity.user.ThemePreference;

/**
 * Respuesta de {@code GET /api/users/preferences} y {@code PATCH /api/users/preferences}.
 */
public record UserPreferencesResponse(
        ThemePreference theme,
        String currency,
        AppLanguage language
) {
}
