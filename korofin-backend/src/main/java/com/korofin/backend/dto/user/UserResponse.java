package com.korofin.backend.dto.user;

import com.korofin.backend.entity.user.AppLanguage;
import com.korofin.backend.entity.user.ThemePreference;

public record UserResponse(
        Long id,
        String name,
        String email,
        ThemePreference theme,
        String currency,
        AppLanguage language
) {
}
