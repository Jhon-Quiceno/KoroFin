-- Dominios debt (deudas) y card (tarjetas de crédito). Consolida en un solo V3 limpio lo que en
-- FinSmart se acumuló en V4 (deudas/pagos), V11/V12 (índice de pagos + FK debt_payment_id en
-- expenses), V15 (cargos de deuda) y V17-V21 (tarjetas, movimientos, planes de cuotas, cuotas y
-- FK card_movement_id en expenses). Ver docs/backend-plan.md secciones 2.3, 2.5 y 2.11.
--
-- Esta migración también cierra dos de las tres FKs futuras que V2 dejó anotadas como pendientes
-- en expenses (debt_payment_id y card_movement_id). La tercera, recurring_payment_id, sigue
-- pendiente hasta la fase del dominio recurringpayment.

-- ---------------------------------------------------------------------------------------------
-- Dominio debt
-- ---------------------------------------------------------------------------------------------

CREATE TABLE debts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    name                VARCHAR(150) NOT NULL,
    total_amount        NUMERIC(15, 2) NOT NULL,
    remaining_amount    NUMERIC(15, 2) NOT NULL,
    interest_rate       NUMERIC(5, 2),
    due_date            DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ,
    -- ON DELETE CASCADE: una deuda no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_debts_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_debts_total_amount_positive CHECK (total_amount > 0),
    -- El saldo restante nunca puede quedar negativo. Este CHECK es una red de seguridad, no la
    -- defensa principal contra abonos que superen el saldo: esa la da el UPDATE atómico
    -- condicional DebtRepository#decrementRemainingAmount (ver su Javadoc).
    CONSTRAINT chk_debts_remaining_amount_non_negative CHECK (remaining_amount >= 0)
);

CREATE INDEX idx_debts_user_id ON debts (user_id);
-- Índice compuesto pensado para el job de recordatorio de pagos (fase de scheduling), que escanea
-- deudas con saldo por fecha de vencimiento.
CREATE INDEX idx_debts_user_due_date ON debts (user_id, due_date);

CREATE TABLE debt_payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    debt_id         BIGINT NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    payment_date    DATE NOT NULL,
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: un abono no tiene sentido sin la deuda a la que se aplicó.
    CONSTRAINT fk_debt_payments_debt FOREIGN KEY (debt_id)
        REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT chk_debt_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_debt_payments_debt_id ON debt_payments (debt_id);

CREATE TABLE debt_charges (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    debt_id         BIGINT NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    charge_date     DATE NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: un cargo no tiene sentido sin la deuda a la que se aplicó.
    CONSTRAINT fk_debt_charges_debt FOREIGN KEY (debt_id)
        REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT chk_debt_charges_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_debt_charges_debt_id ON debt_charges (debt_id);

-- ---------------------------------------------------------------------------------------------
-- Dominio card
-- ---------------------------------------------------------------------------------------------

CREATE TABLE credit_cards (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    name                VARCHAR(150) NOT NULL,
    bank                VARCHAR(100),
    franchise           VARCHAR(20) NOT NULL,
    credit_limit        NUMERIC(15, 2) NOT NULL,
    -- Tasa mensual efectiva (ej. 0.0250 = 2,5% E.M.).
    monthly_rate        NUMERIC(6, 4) NOT NULL,
    cutoff_day          INT NOT NULL,
    payment_due_day     INT NOT NULL,
    current_balance     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    -- Guard de idempotencia del cierre de ciclo: NULL hasta el primer cierre, luego la fecha del
    -- último ciclo cerrado (ver CreditCardRepository#markCutoffClosed).
    last_cutoff_date    DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ,
    -- ON DELETE CASCADE: una tarjeta no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_credit_cards_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_credit_cards_franchise
        CHECK (franchise IN ('VISA', 'MASTERCARD', 'AMEX', 'DINERS')),
    CONSTRAINT chk_credit_cards_credit_limit_positive CHECK (credit_limit > 0),
    CONSTRAINT chk_credit_cards_monthly_rate_non_negative CHECK (monthly_rate >= 0),
    CONSTRAINT chk_credit_cards_cutoff_day CHECK (cutoff_day BETWEEN 1 AND 31),
    CONSTRAINT chk_credit_cards_payment_due_day CHECK (payment_due_day BETWEEN 1 AND 31),
    CONSTRAINT chk_credit_cards_balance_non_negative CHECK (current_balance >= 0)
);

CREATE INDEX idx_credit_cards_user_id ON credit_cards (user_id);
-- Soporta el escaneo cross-user de tarjetas candidatas a cierre de ciclo.
CREATE INDEX idx_credit_cards_last_cutoff_date ON credit_cards (last_cutoff_date);

CREATE TABLE card_movements (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    card_id             BIGINT NOT NULL,
    type                VARCHAR(25) NOT NULL,
    -- Siempre positivo: el efecto sobre el saldo lo determina "type", no el signo del monto.
    amount              NUMERIC(15, 2) NOT NULL,
    -- Se llama "movement_date" y no "date" por la misma convención que charge_date/payment_date
    -- en debt_charges/debt_payments, y para no usar un identificador desnudo "date".
    movement_date       DATE NOT NULL,
    description         VARCHAR(255),
    -- Solo se llena en los movimientos INTEREST agregados que crea el cierre de ciclo; sirve de
    -- rastro de auditoría de qué cierre lo generó. NULL en cualquier otro tipo de movimiento.
    cycle_close_date    DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: un movimiento no tiene sentido sin su tarjeta dueña.
    CONSTRAINT fk_card_movements_card FOREIGN KEY (card_id)
        REFERENCES credit_cards (id) ON DELETE CASCADE,
    CONSTRAINT chk_card_movements_type
        CHECK (type IN ('PURCHASE', 'INSTALLMENT_PURCHASE', 'PAYMENT', 'INTEREST', 'FEE')),
    CONSTRAINT chk_card_movements_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_card_movements_card_date ON card_movements (card_id, movement_date);

CREATE TABLE installment_plans (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movement_id         BIGINT NOT NULL,
    installment_count   INT NOT NULL,
    -- Copia congelada de credit_cards.monthly_rate al momento de la compra; nunca se recalcula
    -- si la tasa de la tarjeta cambia después.
    rate_at_purchase    NUMERIC(6, 4) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: un plan no tiene sentido sin el movimiento que lo originó.
    CONSTRAINT fk_installment_plans_movement FOREIGN KEY (movement_id)
        REFERENCES card_movements (id) ON DELETE CASCADE,
    -- El UNIQUE ya provee el índice de búsqueda por movement_id (Postgres indexa los UNIQUE
    -- automáticamente), así que no hace falta un CREATE INDEX aparte.
    CONSTRAINT uk_installment_plans_movement UNIQUE (movement_id),
    CONSTRAINT chk_installment_plans_count CHECK (installment_count >= 2),
    CONSTRAINT chk_installment_plans_rate_non_negative CHECK (rate_at_purchase >= 0)
);

CREATE TABLE installments (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id                 BIGINT NOT NULL,
    number                  INT NOT NULL,
    capital_amount          NUMERIC(15, 2) NOT NULL,
    interest_amount         NUMERIC(15, 2) NOT NULL,
    due_date                DATE NOT NULL,
    status                  VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    -- Lo asigna el cierre de ciclo cuando materializa el interés de esta cuota en un movimiento
    -- INTEREST agregado; NULL mientras la cuota siga PENDING.
    interest_movement_id    BIGINT,
    created_at              TIMESTAMPTZ NOT NULL,
    -- ON DELETE CASCADE: una cuota no tiene sentido sin su plan dueño.
    CONSTRAINT fk_installments_plan FOREIGN KEY (plan_id)
        REFERENCES installment_plans (id) ON DELETE CASCADE,
    -- ON DELETE SET NULL: si el movimiento de interés agregado se elimina, la cuota (ya
    -- facturada) conserva su registro y solo pierde el vínculo.
    CONSTRAINT fk_installments_interest_movement FOREIGN KEY (interest_movement_id)
        REFERENCES card_movements (id) ON DELETE SET NULL,
    CONSTRAINT chk_installments_status CHECK (status IN ('PENDING', 'BILLED')),
    CONSTRAINT chk_installments_number_positive CHECK (number > 0),
    CONSTRAINT chk_installments_capital_amount_positive CHECK (capital_amount > 0),
    CONSTRAINT chk_installments_interest_amount_non_negative CHECK (interest_amount >= 0)
);

CREATE INDEX idx_installments_plan_status_due ON installments (plan_id, status, due_date);

-- ---------------------------------------------------------------------------------------------
-- FKs de expenses hacia los dos dominios nuevos (ver docs/backend-plan.md sección 2.5)
-- ---------------------------------------------------------------------------------------------

ALTER TABLE expenses ADD COLUMN debt_payment_id BIGINT;
ALTER TABLE expenses ADD COLUMN card_movement_id BIGINT;

-- ON DELETE SET NULL en ambas: borrar el origen (abono a deuda o movimiento de tarjeta) no debe
-- borrar el historial del gasto que generó; el gasto conserva su registro y solo pierde el
-- vínculo con su origen.
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_debt_payment
    FOREIGN KEY (debt_payment_id) REFERENCES debt_payments (id) ON DELETE SET NULL;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_card_movement
    FOREIGN KEY (card_movement_id) REFERENCES card_movements (id) ON DELETE SET NULL;

CREATE INDEX idx_expenses_debt_payment_id ON expenses (debt_payment_id);
CREATE INDEX idx_expenses_card_movement_id ON expenses (card_movement_id);
