package com.korofin.backend.dto.ai;

import java.time.Instant;

/**
 * Respuesta de {@code POST /api/ai/chat}: la respuesta del asistente al mensaje del usuario, más
 * qué proveedor/modelo la produjo.
 *
 * @param reply        el texto de respuesta del asistente, en español
 * @param providerName nombre de despliegue del proveedor de IA que produjo esta respuesta
 * @param model        identificador de modelo que produjo esta respuesta
 * @param createdAt    instante en que se persistió la respuesta del asistente
 */
public record ChatReplyResponse(
        String reply,
        String providerName,
        String model,
        Instant createdAt
) {
}
