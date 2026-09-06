package com.korofin.backend.ai.entity;

/**
 * Autor de un turno de {@link AiMessage} en una conversación.
 *
 * <p>Persistida como columna {@code VARCHAR} con un {@code CHECK} en base de datos (ver
 * {@code V4__create_ai_domain.sql}), mismo criterio simple varchar-más-check del resto del
 * proyecto para enumeraciones.
 */
public enum AiMessageRole {
    USER,
    ASSISTANT
}
