package com.korofin.backend.ai.service.provider;

import com.korofin.backend.ai.entity.AiUsageEventType;

/**
 * Atribución que necesita {@link AiChatOrchestrator} para registrar telemetría de una llamada
 * {@code complete}/{@code completeVision} en nombre del llamador: para quién es la llamada y qué
 * operación asistida por IA representa.
 *
 * <p>{@code ctx == null} es un valor válido y soportado en ambas sobrecargas: significa "no
 * registrar telemetría para esta llamada".
 *
 * @param userId    el usuario en cuyo nombre se hace la llamada de IA
 * @param eventType qué operación asistida por IA representa esta llamada (ver
 *                  {@code AiUsageEventService#recordAttempt})
 */
public record AiCallContext(Long userId, AiUsageEventType eventType) {
}
