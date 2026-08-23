-- Esquema inicial de KoroFin: usuarios y sus refresh tokens. Consolida en un solo V1 limpio lo
-- que en FinSmart se acumuló a lo largo de varias migraciones (V1, V2, V13, V14, V27) porque
-- KoroFin arranca sin datos de producción que migrar (ver docs/backend-plan.md sección 9).

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(180) NOT NULL,
    password_hash   VARCHAR(120) NOT NULL,
    is_active       BOOLEAN NOT NULL,
    last_login_at   TIMESTAMPTZ,
    -- Cuota mensual de chat de IA: contador propio, no una cuenta de filas, para que sobreviva a
    -- la purga del historial de chat en cada login (ver UserService#login) y pueda reservarse
    -- atómicamente. El dominio "ai" todavía no existe (llega en una fase posterior); estas dos
    -- columnas quedan listas desde ya para no requerir otra migración cuando se implemente.
    ai_chat_used    INT NOT NULL DEFAULT 0,
    ai_chat_period  VARCHAR(7),
    -- Preferencias de usuario (tema, moneda, idioma). Columnas escalares 1:1 en users: no hay
    -- necesidad de una tabla user_preferences aparte para tres valores simples sin historial propio.
    theme           VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',
    currency        VARCHAR(3) NOT NULL DEFAULT 'COP',
    language        VARCHAR(5) NOT NULL DEFAULT 'ES',
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_theme CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK')),
    CONSTRAINT chk_users_language CHECK (language IN ('ES', 'EN')),
    CONSTRAINT chk_users_currency CHECK (currency IN ('COP', 'USD', 'MXN', 'ARS', 'EUR'))
);

CREATE TABLE refresh_tokens (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_id        UUID NOT NULL,
    user_id         BIGINT NOT NULL,
    token_hash      VARCHAR(120) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    -- DEFAULT TRUE: mismo criterio que FinSmart para tokens preexistentes; en KoroFin no hay
    -- tokens preexistentes, pero se mantiene el default por consistencia con el comportamiento
    -- esperado cuando el cliente no manda rememberMe explícito.
    remember_me     BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_refresh_tokens_token_id UNIQUE (token_id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    -- ON DELETE CASCADE: borrar un usuario no debe dejar refresh tokens huérfanos.
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- Los constraints UNIQUE de arriba ya crean índices implícitos en token_id y token_hash;
-- user_id no tiene constraint único (un usuario puede tener varios refresh tokens), así que
-- necesita un índice explícito.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
