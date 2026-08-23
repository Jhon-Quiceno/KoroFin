package com.korofin.backend.service.integration.telegram;

import com.korofin.backend.exception.integration.TelegramImplausibleMovementException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae monto y descripción de un mensaje de texto libre de Telegram (p. ej.
 * {@code "Uber 15000"}, {@code "Mercado $45.000"}), con regex — sin IA, la clasificación de
 * categoría/tipo de movimiento la hace {@code AiCategorizationService} después, sobre el resultado
 * ya extraído acá (ver docs/backend-plan.md sección 5).
 *
 * <p>Valida plausibilidad del monto extraído: un mensaje sin ningún número, o con un monto
 * absurdamente bajo/alto para ser un movimiento financiero real, lanza
 * {@link TelegramImplausibleMovementException} con un mensaje amigable que n8n reenvía tal cual al
 * chat — nunca deja pasar un monto sin sentido a la clasificación de IA.
 */
@Component
public class TelegramMessageParser {

    /** Primera secuencia de dígitos (con separadores de miles/decimales opcionales) en el texto. */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d[\\d.,]*\\d|\\d)");

    /** Montos por debajo de este umbral (en la moneda del usuario) no parecen un movimiento real. */
    private static final BigDecimal MIN_PLAUSIBLE_AMOUNT = BigDecimal.valueOf(50);

    /** Montos por encima de este umbral probablemente son un error de tipeo, no un movimiento real. */
    private static final BigDecimal MAX_PLAUSIBLE_AMOUNT = BigDecimal.valueOf(100_000_000);

    private static final String DEFAULT_DESCRIPTION = "Movimiento por Telegram";

    /**
     * @param text el texto libre del mensaje
     * @return el monto y la descripción extraídos
     * @throws TelegramImplausibleMovementException si no se encontró ningún monto en el texto, o
     *         el monto encontrado no es plausible
     */
    public ParsedMovement parse(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new TelegramImplausibleMovementException(
                    "No pude identificar un monto en tu mensaje. Probá escribiendo algo como \"Uber 15000\"."
            );
        }

        String rawAmount = matcher.group(1);
        BigDecimal amount = normalizeAmount(rawAmount);

        if (amount.compareTo(MIN_PLAUSIBLE_AMOUNT) < 0 || amount.compareTo(MAX_PLAUSIBLE_AMOUNT) > 0) {
            throw new TelegramImplausibleMovementException(
                    "El monto que identifiqué (" + amount.toPlainString()
                            + ") no parece válido. Revisá el mensaje e intentá de nuevo."
            );
        }

        String description = buildDescription(text, rawAmount);
        return new ParsedMovement(amount, description);
    }

    /** El texto sin el monto extraído, recortado; si queda vacío, usa {@link #DEFAULT_DESCRIPTION}. */
    private static String buildDescription(String text, String rawAmount) {
        String withoutAmount = text.replaceFirst(Pattern.quote(rawAmount), "").trim();
        withoutAmount = withoutAmount.replaceAll("^[\\s$.,\\-]+|[\\s$.,\\-]+$", "");
        return withoutAmount.isBlank() ? DEFAULT_DESCRIPTION : withoutAmount;
    }

    /**
     * Normaliza un monto crudo con separadores ambiguos: primero remueve separadores de miles
     * (un {@code .}/{@code ,} seguido de exactamente 3 dígitos y luego un no-dígito o el final,
     * ej. {@code "1.234.567"} o {@code "1,234,567"}), y trata cualquier separador restante como
     * punto decimal.
     */
    private static BigDecimal normalizeAmount(String rawAmount) {
        String withoutThousands = rawAmount.replaceAll("[.,](?=\\d{3}(?:\\D|$))", "");
        String withDecimalPoint = withoutThousands.replace(",", ".");
        return new BigDecimal(withDecimalPoint);
    }

    /**
     * Resultado de {@link #parse}.
     *
     * @param amount      el monto extraído, ya validado como plausible
     * @param description el texto restante tras quitar el monto, o {@link #DEFAULT_DESCRIPTION}
     */
    public record ParsedMovement(BigDecimal amount, String description) {
    }
}
