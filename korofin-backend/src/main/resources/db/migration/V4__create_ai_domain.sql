-- Dominio ai de KoroFin: historial de chat/insights de IA (ai_messages) y telemetría de uso de
-- proveedores de IA (ai_usage_events). Consolida en un solo V4 limpio lo que en FinSmart se
-- acumuló en V8 (ai_messages) + V16 (ai_usage_events) + V25 (telemetría por intento: latency_ms/
-- success/error_type) — KoroFin arranca sin datos de producción que migrar (ver
-- docs/backend-plan.md sección 9).

CREATE TABLE ai_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role            VARCHAR(15) NOT NULL,
    kind            VARCHAR(15) NOT NULL,
    content         TEXT NOT NULL,
    provider_name   VARCHAR(60),
    model           VARCHAR(120),
    -- Sin updated_at: las filas son inmutables una vez creadas (ver el Javadoc de AiMessage).
    created_at      TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: el historial de chat/insights no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_ai_messages_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_ai_messages_kind CHECK (kind IN ('CHAT', 'INSIGHT'))
);

-- Índice compuesto: toda consulta real filtra por usuario + kind y ordena por fecha (historial de
-- chat, ventana de continuidad de conversación, último insight generado).
CREATE INDEX idx_ai_messages_user_kind_created_at ON ai_messages (user_id, kind, created_at DESC);

CREATE TABLE ai_usage_events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    provider        VARCHAR(60) NOT NULL,
    event_type      VARCHAR(30) NOT NULL,
    tokens_used     INT NOT NULL DEFAULT 0,
    cost_estimate   NUMERIC(10, 6),
    -- Cuánto tardó este intento puntual, en milisegundos; NULL para las filas escritas por
    -- AiUsageEventService#record (que no lo mide).
    latency_ms      INT,
    -- Si este intento tuvo éxito. TRUE para las filas de #record; para las de #recordAttempt,
    -- FALSE marca un proveedor que hizo failover al siguiente (ver el Javadoc de AiUsageEvent).
    success         BOOLEAN NOT NULL DEFAULT TRUE,
    error_type      VARCHAR(60),
    -- Sin updated_at: las filas son inmutables una vez creadas (ver el Javadoc de AiUsageEvent).
    created_at      TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: la telemetría de uso no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_ai_usage_events_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_usage_events_event_type
        CHECK (event_type IN ('CHAT', 'CATEGORIZE', 'INSIGHT', 'STATEMENT_EXTRACT'))
);

-- Backing de AiUsageEventRepository#aggregateByEventType (resumen de uso por usuario/periodo).
CREATE INDEX idx_ai_usage_events_user_created_at ON ai_usage_events (user_id, created_at);
