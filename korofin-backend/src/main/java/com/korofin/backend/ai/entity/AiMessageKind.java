package com.korofin.backend.ai.entity;

/**
 * Propósito de una fila de {@link AiMessage}.
 *
 * <p>Las filas {@link #CHAT} son turnos de la conversación libre con el asistente
 * ({@code POST /api/ai/chat}); se purgan en cada login ({@code UserService#login}) para que cada
 * sesión nueva arranque con el chat en blanco. Las filas {@link #INSIGHT} son recomendaciones
 * financieras generadas por IA y persistidas para no llamar al proveedor en cada carga de
 * pantalla; sobreviven al login.
 *
 * <p>Persistida como columna {@code VARCHAR} con un {@code CHECK} en base de datos (ver
 * {@code V4__create_ai_domain.sql}).
 */
public enum AiMessageKind {
    CHAT,
    INSIGHT
}
