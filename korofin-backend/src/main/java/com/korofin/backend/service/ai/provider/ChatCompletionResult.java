package com.korofin.backend.service.ai.provider;

/**
 * Resultado de una llamada de chat completions exitosa: el texto de respuesta del asistente, qué
 * proveedor respondió realmente y, cuando el proveedor lo reporta, el consumo de tokens.
 *
 * <p>{@link AiChatClient#complete} no expone metadata de despliegue más allá de lo que necesita
 * para mapear errores, así que devuelve {@code providerName} y {@code model} como {@code null};
 * {@link AiChatOrchestrator#complete} los completa vía {@link #withProvider(String, String)} una
 * vez que sabe qué proveedor configurado respondió, antes de devolver el resultado al llamador.
 *
 * @param content           el texto de respuesta del asistente
 * @param providerName      el nombre del proveedor que produjo esta respuesta, o {@code null}
 *                          hasta que {@link AiChatOrchestrator} lo complete
 * @param model             el identificador de modelo usado para producir esta respuesta, o
 *                          {@code null} hasta que {@link AiChatOrchestrator} lo complete
 * @param promptTokens      tokens consumidos por el prompt, o {@code null} si el proveedor no
 *                          reportó consumo
 * @param completionTokens  tokens consumidos por la respuesta, o {@code null} si el proveedor no
 *                          reportó consumo
 */
public record ChatCompletionResult(
        String content,
        String providerName,
        String model,
        Integer promptTokens,
        Integer completionTokens
) {

    /**
     * Devuelve una copia de este resultado con {@code providerName}/{@code model} completos,
     * manteniendo el resto de los campos igual. Usado por {@link AiChatOrchestrator} una vez que
     * sabe qué proveedor configurado respondió realmente.
     */
    ChatCompletionResult withProvider(String providerName, String model) {
        return new ChatCompletionResult(content, providerName, model, promptTokens, completionTokens);
    }
}
