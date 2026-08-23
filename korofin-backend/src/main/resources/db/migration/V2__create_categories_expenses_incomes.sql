-- Categorías, gastos e ingresos de KoroFin. Consolida en un solo V2 limpio lo que en FinSmart
-- era V3 (categorías/ingresos/gastos) más V5/V21 (FKs de expenses hacia servicios
-- recurrentes/pagos de deuda/movimientos de tarjeta) — esas tres FKs NO se incluyen todavía en
-- esta fase porque los dominios recurringpayment/debt/card no existen aún (ver
-- docs/backend-plan.md sección 2.5 y el Javadoc de Expense.java). Cuando se implementen, cada
-- fase agrega su propia migración ALTER TABLE expenses ADD COLUMN ... para su FK.

CREATE TABLE categories (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    -- ON DELETE CASCADE: una categoría no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_categories_user_name_type UNIQUE (user_id, name, type),
    CONSTRAINT chk_categories_type CHECK (type IN ('EXPENSE', 'INCOME'))
);

CREATE INDEX idx_categories_user_id ON categories (user_id);

CREATE TABLE expenses (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    category_id     BIGINT,
    amount          NUMERIC(15, 2) NOT NULL,
    description     VARCHAR(255),
    date            DATE NOT NULL,
    payment_method  VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    -- ON DELETE CASCADE: el historial de gastos no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_expenses_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- ON DELETE SET NULL: borrar una categoría no debe borrar el historial de gastos que la
    -- referenciaban; el gasto queda sin clasificar en su lugar.
    CONSTRAINT fk_expenses_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT chk_expenses_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_expenses_payment_method
        CHECK (payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER', 'OTHER'))
);

CREATE INDEX idx_expenses_user_id ON expenses (user_id);
CREATE INDEX idx_expenses_date ON expenses (date);
CREATE INDEX idx_expenses_category_id ON expenses (category_id);

CREATE TABLE incomes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    category_id     BIGINT,
    amount          NUMERIC(15, 2) NOT NULL,
    description     VARCHAR(255),
    date            DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    -- ON DELETE CASCADE: el historial de ingresos no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_incomes_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- ON DELETE SET NULL: borrar una categoría no debe borrar el historial de ingresos que la
    -- referenciaban; el ingreso queda sin clasificar en su lugar.
    CONSTRAINT fk_incomes_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT chk_incomes_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_incomes_user_id ON incomes (user_id);
CREATE INDEX idx_incomes_date ON incomes (date);
CREATE INDEX idx_incomes_category_id ON incomes (category_id);
