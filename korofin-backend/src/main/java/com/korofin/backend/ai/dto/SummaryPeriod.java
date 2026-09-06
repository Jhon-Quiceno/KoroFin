package com.korofin.backend.ai.dto;

/**
 * El rango de fechas al que se refiere una pregunta de resumen financiero, según lo decide
 * {@code FinancialSummaryQueryService#parseQuery} a partir de una pregunta en lenguaje natural.
 */
public enum SummaryPeriod {
    /** Solo hoy ({@code [hoy, hoy]}). */
    TODAY,
    /** Ventana móvil de 7 días terminando hoy ({@code [hoy - 6 días, hoy]}). */
    WEEK,
    /** Mes en curso ({@code [primer día del mes actual, hoy]}). */
    MONTH,
    /** El mes calendario anterior completo. */
    LAST_MONTH,
    /** Año en curso ({@code [1 de enero del año actual, hoy]}). */
    YEAR
}
