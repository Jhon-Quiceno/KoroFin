package com.korofin.backend.user.entity;

/**
 * Preferencia de tema visual del usuario, aplicada en la app móvil. {@code SYSTEM} sigue el tema
 * del sistema operativo; {@code LIGHT}/{@code DARK} lo fuerzan.
 *
 * <p>Persistida como {@code VARCHAR} con un {@code CHECK} constraint (ver
 * {@code V1__create_users_and_refresh_tokens.sql}).
 */
public enum ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}
