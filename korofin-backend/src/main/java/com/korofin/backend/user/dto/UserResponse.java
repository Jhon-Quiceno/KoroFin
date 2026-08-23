package com.korofin.backend.user.dto;

import com.korofin.backend.user.entity.AppLanguage;
import com.korofin.backend.user.entity.ThemePreference;

public record UserResponse(
        Long id,
        String name,
        String email,
        ThemePreference theme,
        String currency,
        AppLanguage language
) {
}
