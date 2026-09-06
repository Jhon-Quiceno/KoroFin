package com.korofin.backend.integration.service.telegram;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Heurística de palabras clave (SIN IA) para distinguir una pregunta de resumen financiero
 * (p. ej. {@code "¿cuánto gasté en comida?"}) de un intento de registro de movimiento
 * (p. ej. {@code "Uber 15000"}), evitando pagar una llamada de IA extra en cada mensaje solo para
 * decidir esto (ver docs/backend-plan.md sección 5).
 *
 * <p>Es deliberadamente conservadora hacia "pregunta": un signo de interrogación o cualquiera de
 * las palabras clave típicas de una consulta alcanza para clasificar como pregunta, porque el
 * costo de tratar erróneamente una pregunta como intento de registro (falla al parsear un monto
 * plausible, {@code TelegramImplausibleMovementException}) es peor experiencia de usuario que lo
 * inverso.
 */
@Component
public class TelegramIntentDetector {

    private static final List<String> QUESTION_KEYWORDS = List.of(
            "cuanto", "cuánto", "cuanta", "cuánta",
            "como voy", "cómo voy", "como estoy", "cómo estoy",
            "resumen", "balance", "gaste", "gasté", "gasto en", "cuanto llevo", "cuánto llevo"
    );

    /**
     * @param text el texto libre del mensaje
     * @return {@code true} si el mensaje parece una pregunta de resumen financiero en vez de un
     *         intento de registrar un movimiento
     */
    public boolean looksLikeSummaryQuery(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("?") || QUESTION_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}
