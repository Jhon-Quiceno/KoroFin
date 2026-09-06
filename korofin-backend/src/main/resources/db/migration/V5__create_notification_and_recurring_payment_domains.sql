-- Dominios notification y recurringpayment de KoroFin, más la tercera FK pendiente de expenses
-- (recurring_payment_id, ver el Javadoc de Expense.java y docs/backend-plan.md sección 2.5/2.10).
-- Consolida en un solo V5 limpio lo que en FinSmart se acumuló en V7 (notificaciones/
-- preferencias), V4/V5 (pagos recurrentes + FK en expenses), V22 (tipo de notificación de cierre
-- de ciclo) y V26 (push tokens) — KoroFin arranca sin datos de producción que migrar.

-- ---------------------------------------------------------------------------------------------
-- Dominio recurringpayment (se crea antes que notification: expenses.recurring_payment_id y
-- ningún otro objeto de este archivo depende de notification)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE recurring_payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    name                VARCHAR(150) NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL,
    frequency           VARCHAR(20) NOT NULL,
    next_payment_date   DATE NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ,
    -- ON DELETE CASCADE: un pago recurrente no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_recurring_payments_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_recurring_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_recurring_payments_frequency CHECK (frequency IN ('MONTHLY', 'WEEKLY'))
);

CREATE INDEX idx_recurring_payments_user_id ON recurring_payments (user_id);
-- Soporta el escaneo cross-user de PaymentReminderJob (recurrentes activos por fecha de vencimiento).
CREATE INDEX idx_recurring_payments_active_next_date ON recurring_payments (is_active, next_payment_date);

-- ---------------------------------------------------------------------------------------------
-- Tercera FK pendiente de expenses (ver el Javadoc de Expense.java: debt_payment_id y
-- card_movement_id ya se agregaron en V3; esta es la última de las tres)
-- ---------------------------------------------------------------------------------------------

ALTER TABLE expenses ADD COLUMN recurring_payment_id BIGINT;

-- ON DELETE SET NULL: borrar el pago recurrente no debe borrar el historial del gasto que generó;
-- el gasto conserva su registro y solo pierde el vínculo con su origen.
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_recurring_payment
    FOREIGN KEY (recurring_payment_id) REFERENCES recurring_payments (id) ON DELETE SET NULL;

CREATE INDEX idx_expenses_recurring_payment_id ON expenses (recurring_payment_id);

-- ---------------------------------------------------------------------------------------------
-- Dominio notification
-- ---------------------------------------------------------------------------------------------

CREATE TABLE notifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(40) NOT NULL,
    title           VARCHAR(150) NOT NULL,
    message         TEXT NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    -- Único por (user_id, dedupe_key) cuando no es NULL (ver el índice único parcial más abajo):
    -- lo que permite a un job programado invocarse repetidamente para el mismo (usuario, target,
    -- período) sin crear filas duplicadas. Notificaciones manuales/puntuales pueden dejarlo NULL.
    dedupe_key      VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    -- ON DELETE CASCADE: el historial de notificaciones no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type
        CHECK (type IN ('PAYMENT_REMINDER', 'OVERSPEND_ALERT', 'WEEKLY_SUMMARY',
                         'INACTIVITY_REMINDER', 'MONTH_END_PREDICTION', 'SYSTEM',
                         'CARD_CYCLE_CLOSE'))
);

CREATE INDEX idx_notifications_user_created_at ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE is_read = FALSE;
-- Índice único parcial: solo aplica cuando dedupe_key no es NULL, así que las notificaciones sin
-- dedupe_key (manuales/puntuales) nunca compiten por unicidad entre sí.
CREATE UNIQUE INDEX uk_notifications_user_dedupe_key
    ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE notification_preferences (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL,
    payment_reminders       BOOLEAN NOT NULL DEFAULT TRUE,
    overspend_alerts        BOOLEAN NOT NULL DEFAULT TRUE,
    weekly_summary          BOOLEAN NOT NULL DEFAULT TRUE,
    inactivity_reminders    BOOLEAN NOT NULL DEFAULT TRUE,
    card_cycle_close        BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ,
    -- ON DELETE CASCADE: las preferencias no tienen sentido sin su usuario dueño.
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- Como mucho una fila por usuario (NotificationService#findOrCreatePreference la crea perezosamente).
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id)
);

CREATE TABLE push_tokens (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    expo_push_token     VARCHAR(200) NOT NULL,
    device_id           VARCHAR(120) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: un token push no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- Un token por (usuario, dispositivo): reinstalar la app o rotar el token de Expo actualiza la
    -- fila existente en vez de acumular duplicados.
    CONSTRAINT uk_push_tokens_user_device UNIQUE (user_id, device_id)
);
