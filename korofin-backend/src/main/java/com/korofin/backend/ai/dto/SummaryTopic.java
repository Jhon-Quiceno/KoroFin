package com.korofin.backend.ai.dto;

/**
 * A qué tipo de pregunta financiera se refiere una pregunta en lenguaje natural, según lo decide
 * {@code FinancialSummaryQueryService#parseQuery}.
 */
public enum SummaryTopic {
    /** Una pregunta sobre movimientos (gastos/ingresos/balance) — el default. */
    MOVEMENT,
    /** Una pregunta sobre deudas (p. ej. "¿cómo voy con mis deudas?", "cuánto debo"). */
    DEBT
}
