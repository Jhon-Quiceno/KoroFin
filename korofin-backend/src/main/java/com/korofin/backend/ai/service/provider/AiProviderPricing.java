package com.korofin.backend.ai.service.provider;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Estimaciones de costo aproximado por 1000 tokens para los proveedores del catálogo de
 * {@link SupportedAiProvider}, usadas por {@link AiChatOrchestrator} para completar
 * {@code AiUsageEvent#costEstimate} del intento ganador de cada llamada
 * {@code complete}/{@code completeVision} hecha con un {@link AiCallContext}.
 *
 * <p>Deliberadamente una tabla estática simple — no hay tabla de precios respaldada por
 * configuración o base de datos todavía. Los precios de proveedores pagos de abajo son
 * placeholders, no verificados contra la página de precios actual de cada proveedor; revisar una
 * vez que el gasto real pueda conciliarse contra estas estimaciones.
 *
 * @see AiUsageEventService
 */
@Component
public class AiProviderPricing {

    private static final BigDecimal THOUSAND_TOKENS = BigDecimal.valueOf(1000);

    /** Precio placeholder para los endpoints NIM pagos de NVIDIA — NO verificado. */
    private static final BigDecimal NVIDIA_PRICE_PER_1000_TOKENS = BigDecimal.valueOf(0.002);

    /**
     * Precio placeholder para el tier pago de Groq — Groq no tiene API key configurada en este
     * proyecto todavía (ver {@link SupportedAiProvider#GROQ}), así que es una estimación
     * razonable, NO verificada.
     */
    private static final BigDecimal GROQ_PRICE_PER_1000_TOKENS = BigDecimal.valueOf(0.001);

    /**
     * @param providerKey la {@link SupportedAiProvider#key()} del proveedor (p. ej. {@code "nvidia"})
     * @param model       el identificador de modelo exacto usado para la llamada
     * @param tokensUsed  tokens totales consumidos (prompt + respuesta) por la llamada
     * @return {@link BigDecimal#ZERO} para un modelo gratuito conocido, o cuando
     *         {@code tokensUsed <= 0}; la estimación de costo, redondeada a 6 decimales
     *         (coincidiendo con la precisión de la columna {@code AiUsageEvent#costEstimate}),
     *         para un proveedor pago conocido (NVIDIA, Groq); o {@code null} cuando esta
     *         combinación proveedor/modelo no tiene ningún precio conocido
     */
    public BigDecimal estimateCost(String providerKey, String model, int tokensUsed) {
        if (tokensUsed <= 0) {
            return BigDecimal.ZERO;
        }
        if (isKnownFreeModel(providerKey, model)) {
            return BigDecimal.ZERO;
        }
        BigDecimal pricePerThousandTokens = pricePerThousandTokens(providerKey);
        if (pricePerThousandTokens == null) {
            return null;
        }
        return pricePerThousandTokens
                .multiply(BigDecimal.valueOf(tokensUsed))
                .divide(THOUSAND_TOKENS, 6, RoundingMode.HALF_UP);
    }

    /**
     * @return {@code true} para el modelo gratuito por defecto conocido de OpenCode, o cualquier
     *         modelo que siga la convención de sufijo {@code :free} de OpenRouter (sin distinguir
     *         mayúsculas/minúsculas)
     */
    private static boolean isKnownFreeModel(String providerKey, String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        if (model.toLowerCase(Locale.ROOT).endsWith(":free")) {
            return true;
        }
        return SupportedAiProvider.OPENCODE.key().equals(providerKey)
                && SupportedAiProvider.OPENCODE.defaultModel().equals(model);
    }

    /**
     * @return el precio placeholder conocido para un proveedor pago (NVIDIA, Groq), o {@code null}
     *         para modelos no gratuitos de OpenCode/OpenRouter (sin datos de precio todavía) o
     *         cualquier {@code providerKey} no reconocido
     */
    private static BigDecimal pricePerThousandTokens(String providerKey) {
        if (SupportedAiProvider.NVIDIA.key().equals(providerKey)) {
            return NVIDIA_PRICE_PER_1000_TOKENS;
        }
        if (SupportedAiProvider.GROQ.key().equals(providerKey)) {
            return GROQ_PRICE_PER_1000_TOKENS;
        }
        return null;
    }
}
