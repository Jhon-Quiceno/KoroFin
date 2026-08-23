package com.korofin.backend.service.ai.provider;

import com.korofin.backend.exception.ai.AiProviderAuthException;
import com.korofin.backend.exception.ai.AiProviderException;
import com.korofin.backend.exception.ai.AiProviderModelNotFoundException;
import com.korofin.backend.exception.ai.AiProviderRateLimitException;
import com.korofin.backend.exception.ai.AiProviderTimeoutException;
import com.korofin.backend.exception.ai.AiProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;

/**
 * Cliente único de chat completions OpenAI-compatible, compartido por todos los proveedores de IA
 * que soporta este proyecto (Gemini, NVIDIA NIM, Groq, OpenRouter, OpenCode, o cualquier otro
 * proveedor que hable el mismo protocolo) — ver {@code docs/backend-plan.md} sección 4.
 *
 * <p>Se construye un {@link RestClient} nuevo por llamada (vía {@link RestClient.Builder#clone()})
 * con el {@link ResolvedAiProvider#baseUrl()} del proveedor específico que se está llamando. El
 * bean {@link RestClient.Builder} subyacente (ver
 * {@code com.korofin.backend.config.RestClientConfig}) se reutiliza tal cual — en particular, esta
 * clase nunca toca su request factory, así que los timeouts de conexión/lectura configurados ahí
 * sobreviven cada {@code clone()}, y un test puede sustituir un {@code RestClient.Builder}
 * enlazado a {@code MockRestServiceServer} sin que esta clase pise el request factory simulado.
 *
 * <p>Toda respuesta no-2xx o falla de conexión se traduce a una subclase específica de
 * {@link AiProviderException} (ver {@link #mapResponseException} y {@link #mapAccessException})
 * para que los llamadores nunca necesiten conocer {@code RestClientException}.
 */
@Service
public class AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(AiChatClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final RestClient.Builder restClientBuilder;

    public AiChatClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * Envía {@code messages} al endpoint {@code /chat/completions} de {@code provider} y devuelve
     * la respuesta del asistente.
     *
     * @param provider la configuración del proveedor de IA a llamar (URL base, API key y modelo)
     * @param messages la conversación completa a enviar, en orden (prompt de sistema primero)
     * @return la respuesta del asistente y, cuando se reporta, el consumo de tokens
     * @throws AiProviderException si el proveedor rechaza el request o no se pudo alcanzar
     */
    public ChatCompletionResult complete(ResolvedAiProvider provider, List<ChatMessage> messages) {
        RestClient client = buildClient(provider.baseUrl());
        ChatCompletionRequest requestBody = new ChatCompletionRequest(
                provider.model(),
                messages.stream().map(AiChatClient::toOpenAiMessage).toList(),
                false,
                buildChatTemplateKwargs(provider)
        );

        try {
            ChatCompletionResponse response = client.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            return toResult(provider.name(), response);
        } catch (RestClientResponseException ex) {
            // AiProviderException y sus subclases llevan a propósito solo un mensaje genérico de
            // cara al usuario (ver su Javadoc), descartando la causa real - este es el único lugar
            // donde ese detalle sigue disponible antes de perderse.
            log.warn("ai_provider_http_error provider={} status={} body={}",
                    provider.name(), ex.getStatusCode(), truncate(ex.getResponseBodyAsString()));
            throw mapResponseException(provider.name(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("ai_provider_connection_error provider={} message={}", provider.name(), ex.getMessage());
            throw mapAccessException(provider.name(), ex);
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }

    /**
     * Los modelos más nuevos de la familia Nemotron de NVIDIA por defecto usan un modo híbrido de
     * "razonamiento" verboso: sin esta bandera gastan todo el presupuesto de tokens en
     * chain-of-thought y nunca emiten la respuesta JSON real que piden los prompts de esta app,
     * truncando con {@code finish_reason: "length"}. Es una extensión propia de NVIDIA (no parte
     * del spec de chat-completions de OpenAI), inofensiva de enviar incluso cuando el modelo
     * configurado no tiene modo "thinking" en absoluto. Se aplica sin condición para cualquier
     * modelo de NVIDIA (no solo el configurado ahora mismo), para que un futuro cambio de
     * {@code NVIDIA_MODEL} no reintroduzca silenciosamente esta falla.
     */
    private static ChatCompletionRequest.ChatTemplateKwargs buildChatTemplateKwargs(ResolvedAiProvider provider) {
        return SupportedAiProvider.NVIDIA.key().equals(provider.name())
                ? new ChatCompletionRequest.ChatTemplateKwargs(false)
                : null;
    }

    /**
     * Convierte un {@link ChatMessage} al {@link OpenAiMessage} de nivel de red, eligiendo entre
     * las dos formas de contenido que acepta el contrato de chat-completions de OpenAI: un string
     * plano para un turno de texto normal, o un arreglo de partes de contenido
     * ({@code [{"type":"text",...},{"type":"image_url",...}]}) cuando
     * {@link ChatMessage#imageUrl()} está fijado.
     */
    private static OpenAiMessage toOpenAiMessage(ChatMessage message) {
        if (message.imageUrl() == null) {
            return new OpenAiMessage(message.role(), message.content());
        }
        return new OpenAiMessage(message.role(), List.of(
                ContentPart.text(message.content()),
                ContentPart.imageUrl(message.imageUrl())
        ));
    }

    private RestClient buildClient(String baseUrl) {
        return restClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .build();
    }

    /**
     * Quita una barra final de {@code baseUrl}, ya que esta clase siempre agrega
     * {@link #CHAT_COMPLETIONS_PATH} con su propia barra inicial — los proveedores están
     * configurados con o sin barra final de forma inconsistente.
     */
    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Una respuesta 200 sin choices (p. ej. una respuesta de Gemini filtrada por seguridad, o un
     * proveedor devolviendo un cuerpo vacío inesperado) se trata como una falla del proveedor —no
     * un bug de este cliente— así que dispara el failover de
     * {@link com.korofin.backend.service.ai.provider.AiChatOrchestrator} al siguiente proveedor
     * configurado en vez de escapar como una excepción sin capturar.
     */
    private static ChatCompletionResult toResult(String providerName, ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiProviderUnavailableException(providerName);
        }
        String content = response.choices().get(0).message().content();
        ChatCompletionResponse.Usage usage = response.usage();
        Integer promptTokens = usage != null ? usage.promptTokens() : null;
        Integer completionTokens = usage != null ? usage.completionTokens() : null;
        return new ChatCompletionResult(content, null, null, promptTokens, completionTokens);
    }

    /**
     * Mapea una falla a nivel HTTP (4xx/5xx) a la subclase de {@link AiProviderException} que
     * corresponde. Package-private para que {@code AiChatClientTest} pueda ejercitar casos de
     * borde directamente sin necesitar un round trip completo de {@link RestClient} para cada uno.
     */
    AiProviderException mapResponseException(String providerName, RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return new AiProviderAuthException(providerName);
        }
        if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return new AiProviderRateLimitException(providerName);
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND) || bodyMentionsModel(ex.getResponseBodyAsString())) {
            return new AiProviderModelNotFoundException(providerName);
        }
        return new AiProviderUnavailableException(providerName);
    }

    /**
     * Mapea una falla a nivel de conexión (sin respuesta HTTP en absoluto) a la subclase de
     * {@link AiProviderException} que corresponde, distinguiendo un timeout de cualquier otra
     * falla de conectividad (conexión rechazada, falla de DNS, etc.) inspeccionando la causa.
     */
    AiProviderException mapAccessException(String providerName, ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
            return new AiProviderTimeoutException(providerName);
        }
        return new AiProviderUnavailableException(providerName);
    }

    private static boolean bodyMentionsModel(String responseBody) {
        return responseBody != null && responseBody.toLowerCase(Locale.ROOT).contains("model");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(
            String model,
            List<OpenAiMessage> messages,
            boolean stream,
            @JsonProperty("chat_template_kwargs") ChatTemplateKwargs chatTemplateKwargs
    ) {
        private record ChatTemplateKwargs(Boolean thinking) {
        }
    }

    /**
     * Mensaje saliente a nivel de red. {@code content} es deliberadamente {@link Object} y no
     * {@link String}: contiene un {@link String} plano (un turno de texto normal) o una
     * {@code List<ContentPart>} (un turno de visión), y se serializa cada forma tal cual — un
     * string JSON en el primer caso, un arreglo JSON de objetos {@code {"type":...}} en el
     * segundo. Ver {@link #toOpenAiMessage(ChatMessage)}.
     */
    private record OpenAiMessage(String role, Object content) {
    }

    /**
     * Un elemento del arreglo {@code content} de visión de OpenAI. Solo uno de
     * {@code text}/{@code imageUrl} está fijado por instancia (ver {@link #text(String)}/
     * {@link #imageUrl(String)}); el otro se omite del JSON vía {@code @JsonInclude(NON_NULL)}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ContentPart(String type, String text, @JsonProperty("image_url") ImageUrlPart imageUrl) {

        private static final String TYPE_TEXT = "text";
        private static final String TYPE_IMAGE_URL = "image_url";

        private static ContentPart text(String text) {
            return new ContentPart(TYPE_TEXT, text, null);
        }

        private static ContentPart imageUrl(String url) {
            return new ContentPart(TYPE_IMAGE_URL, null, new ImageUrlPart(url));
        }

        private record ImageUrlPart(String url) {
        }
    }

    private record ChatCompletionResponse(List<Choice> choices, Usage usage) {

        /**
         * La respuesta del proveedor siempre es texto plano en este proyecto (ningún proveedor que
         * se llame responde con un arreglo {@code content} multimodal), así que este tipo del lado
         * de la respuesta mantiene {@code content} como {@link String} plano — distinto del
         * {@link OpenAiMessage} del lado del request, cuyo {@code content} debe ser polimórfico
         * para soportar turnos de visión.
         */
        private record Choice(ResponseMessage message) {
        }

        private record ResponseMessage(String role, String content) {
        }

        private record Usage(
                @JsonProperty("prompt_tokens") Integer promptTokens,
                @JsonProperty("completion_tokens") Integer completionTokens
        ) {
        }
    }
}
