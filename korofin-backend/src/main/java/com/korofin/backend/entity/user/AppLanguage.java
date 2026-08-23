package com.korofin.backend.entity.user;

/**
 * Idioma preferido del usuario. Persistido y expuesto por {@code /api/users/preferences}.
 *
 * <p>Persistida como {@code VARCHAR} con un {@code CHECK} constraint (ver
 * {@code V1__create_users_and_refresh_tokens.sql}).
 */
public enum AppLanguage {
    ES,
    EN
}
