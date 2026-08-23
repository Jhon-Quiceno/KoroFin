package com.korofin.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de {@code POST /api/ai/chat}: un mensaje libre del usuario hacia el asistente.
 *
 * @param message el mensaje del usuario, en cualquier idioma (el asistente responde siempre en
 *                español, ver {@code FinancialContextBuilder})
 */
public record ChatRequest(
        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 4000, message = "El mensaje no puede superar 4000 caracteres")
        String message
) {
}
