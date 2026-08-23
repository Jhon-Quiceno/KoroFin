-- Dominio integration de KoroFin (fase 7, integración de Telegram). Vincula un chat de Telegram
-- con un usuario de KoroFin — ver docs/backend-plan.md sección 5 y el Javadoc de
-- com.korofin.backend.entity.integration.TelegramLink.

CREATE TABLE telegram_links (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    telegram_chat_id    BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ,
    -- ON DELETE CASCADE: un vínculo de Telegram no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_telegram_links_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- Un chat de Telegram vincula a lo sumo un usuario a la vez; si el chat ya estaba vinculado a
    -- otro usuario, TelegramLinkService#confirmLink re-asigna la fila existente en vez de fallar.
    CONSTRAINT uk_telegram_links_chat_id UNIQUE (telegram_chat_id)
);

CREATE INDEX idx_telegram_links_user_id ON telegram_links (user_id);
