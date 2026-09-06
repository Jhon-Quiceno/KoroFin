package com.korofin.backend.ai.entity;

/**
 * Qué operación asistida por IA produjo una fila de {@link AiUsageEvent}: una respuesta de chat,
 * una sugerencia de categoría, un insight financiero generado, o (en una fase posterior) una
 * extracción de movimientos de un extracto bancario.
 *
 * <p>Persistida como columna {@code VARCHAR} con un {@code CHECK} en base de datos (ver
 * {@code V4__create_ai_domain.sql}).
 */
public enum AiUsageEventType {
    CHAT,
    CATEGORIZE,
    INSIGHT,
    STATEMENT_EXTRACT
}
