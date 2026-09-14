package com.korofin.backend.ai.service.provider;

import java.util.Locale;
import java.util.Optional;

/**
 * Catálogo fijo de proveedores de IA que este proyecto sabe llamar a través del contrato
 * OpenAI-compatible {@code /chat/completions} de {@link AiChatClient}.
 *
 * <p>Cada entrada fija un {@code baseUrl} y un {@code defaultModel} razonable, para que el
 * operador de la app solo necesite configurar una API key (y, opcionalmente, un modelo distinto)
 * en el entorno para activar un proveedor — ver {@code docs/backend-plan.md} sección 4.
 */
public enum SupportedAiProvider {

    // "meta/llama-3.2-11b-vision-instruct" es multimodal y sirve para texto y para imagen, por eso
    // el 3er argumento repite el 2do. Reemplaza a "meta/llama-3.1-70b-instruct" y a
    // "nvidia/nemotron-nano-12b-v2-vl", que NVIDIA dio de baja el 2026-08-26: pedirlos hoy
    // devuelve HTTP 410 Gone.
    NVIDIA("https://integrate.api.nvidia.com/v1", "meta/llama-3.2-11b-vision-instruct", "meta/llama-3.2-11b-vision-instruct"),
    // Endpoint OpenAI-compatible oficial de Gemini (no la API nativa de Google). "gemini-3.5-flash"
    // es multimodal: el mismo modelo sirve para texto y para imagen, por eso el 3er argumento
    // repite el 2do.
    GEMINI("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.5-flash", "gemini-3.5-flash"),
    // El catálogo de OpenCode no publica ningún modelo gratuito utilizable: el anterior default
    // "deepseek-v4-flash-free" responde "Model is unavailable" y el resto exige un método de pago
    // en el workspace. Se deja el que su documentación lista como gratuito para que la entrada no
    // quede sin default, pero este proveedor no va a responder sin una cuenta con pago habilitado.
    OPENCODE("https://opencode.ai/zen/v1", "deepseek-v4-flash-free", null),
    // El sufijo ":free" dejó de existir en OpenRouter y devuelve 404; el mismo slug sin sufijo sí
    // responde. El de visión se cambia porque el nemotron-nano-12b-v2-vl que usaba antes fue dado
    // de baja junto con la variante de NVIDIA.
    OPENROUTER("https://openrouter.ai/api/v1", "nvidia/nemotron-3-nano-30b-a3b", "inclusionai/ling-3.0-flash-vl"),
    // Groq no expone modelos de visión en su catálogo OpenAI-compatible, de ahí el null.
    // "llama-3.3-70b-versatile" ya no está disponible (404).
    GROQ("https://api.groq.com/openai/v1", "openai/gpt-oss-120b", null);

    private final String baseUrl;
    private final String defaultModel;
    private final String defaultVisionModel;

    SupportedAiProvider(String baseUrl, String defaultModel, String defaultVisionModel) {
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.defaultVisionModel = defaultVisionModel;
    }

    /**
     * @return la URL base fija de la API OpenAI-compatible de este proveedor
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * @return el modelo usado cuando el operador fija la API key de este proveedor pero no un
     *         modelo explícito
     */
    public String defaultModel() {
        return defaultModel;
    }

    /**
     * @return el modelo usado para llamadas con imagen (ver
     *         {@code AiChatOrchestrator#completeVision}) cuando el operador no fija un
     *         {@code vision-model} explícito, o {@code null} si este proveedor no tiene un modelo
     *         de visión conocido en el catálogo. Solo NVIDIA, GEMINI y OPENROUTER tienen un valor
     *         no nulo hoy; {@code completeVision} recorre cada proveedor habilitado en orden de
     *         prioridad y salta cualquiera cuyo valor acá sea {@code null}/vacío.
     */
    public String defaultVisionModel() {
        return defaultVisionModel;
    }

    /**
     * @return la clave en minúscula que identifica a este proveedor en
     *         {@code application.properties} (p. ej. {@code "nvidia"}), coincidiendo con las
     *         claves del mapa de proveedores de {@link AiProviderProperties}
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Busca un proveedor por su {@link #key()}, sin distinguir mayúsculas/minúsculas.
     *
     * @param key la clave candidata (p. ej. {@code "Nvidia"}, {@code "NVIDIA"}, {@code "nvidia"})
     * @return el proveedor que coincide, o {@link Optional#empty()} si {@code key} es
     *         {@code null}, vacía, o no coincide con ningún proveedor conocido
     */
    public static Optional<SupportedAiProvider> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (SupportedAiProvider provider : values()) {
            if (provider.key().equals(normalized)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
