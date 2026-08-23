package com.korofin.backend.ai.dto;

import com.korofin.backend.ai.entity.AiMessageRole;

import java.time.Instant;

/**
 * Un turno único en {@code GET /api/ai/chat/history}.
 *
 * <p>{@link #providerName} y {@link #model} son {@code null} para turnos {@link AiMessageRole#USER}
 * y están completos para turnos {@link AiMessageRole#ASSISTANT}.
 *
 * @param id           identificador del mensaje
 * @param role         autor de este turno
 * @param content      texto del mensaje
 * @param providerName proveedor de IA que produjo este turno, o {@code null} para un turno de usuario
 * @param model        modelo que produjo este turno, o {@code null} para un turno de usuario
 * @param createdAt    instante en que se persistió este turno
 */
public record ChatMessageResponse(
        Long id,
        AiMessageRole role,
        String content,
        String providerName,
        String model,
        Instant createdAt
) {
}
