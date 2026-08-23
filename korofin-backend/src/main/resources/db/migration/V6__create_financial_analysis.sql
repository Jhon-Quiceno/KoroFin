-- Dominio analysis de KoroFin: snapshot persistido del resumen financiero mensual de cada
-- usuario. FinancialAnalysisService recalcula las cifras en cada GET /api/analysis/summary
-- (fuente de verdad: expenses/incomes), pero guarda (upsert) el resultado del mes consultado acá
-- para dejar un historial consultable sin tener que re-agregar expenses/incomes cada vez que algo
-- necesite el número ya calculado de un período pasado (ver docs/backend-plan.md sección 2.12).

CREATE TABLE financial_analysis (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    period_year     INT NOT NULL,
    period_month    INT NOT NULL,
    total_income    NUMERIC(15, 2) NOT NULL,
    total_expense   NUMERIC(15, 2) NOT NULL,
    total_savings   NUMERIC(15, 2) NOT NULL,
    savings_rate    NUMERIC(6, 2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    -- ON DELETE CASCADE: un snapshot de análisis no tiene sentido sin su usuario dueño.
    CONSTRAINT fk_financial_analysis_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- A lo sumo un snapshot por (usuario, mes calendario) — FinancialAnalysisService hace upsert
    -- sobre esta unicidad en vez de acumular una fila nueva cada vez que se pide el resumen.
    CONSTRAINT uk_financial_analysis_user_period UNIQUE (user_id, period_year, period_month),
    CONSTRAINT chk_financial_analysis_month CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX idx_financial_analysis_user_period ON financial_analysis (user_id, period_year, period_month);
