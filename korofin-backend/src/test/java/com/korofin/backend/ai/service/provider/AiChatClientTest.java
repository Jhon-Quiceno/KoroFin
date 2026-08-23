package com.korofin.backend.ai.service.provider;

import com.korofin.backend.ai.exception.AiProviderAuthException;
import com.korofin.backend.ai.exception.AiProviderException;
import com.korofin.backend.ai.exception.AiProviderModelNotFoundException;
import com.korofin.backend.ai.exception.AiProviderRateLimitException;
import com.korofin.backend.ai.exception.AiProviderTimeoutException;
import com.korofin.backend.ai.exception.AiProviderUnavailableException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * {@link AiChatClient#mapResponseException}/{@link AiChatClient#mapAccessException} son
 * package-private a propósito (ver su Javadoc) para poder testear el mapeo de errores directo, sin
 * levantar infraestructura HTTP real para cada caso de borde; el happy-path sí monta
 * {@link MockRestServiceServer} contra el contrato real (mejora respecto a FinSmart, ver
 * {@code docs/backend-plan.md} sección 15.1).
 */
class AiChatClientTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final AiChatClient aiChatClient = new AiChatClient(restClientBuilder);

    @Test
    void completeShouldPostConversationAndReturnAssistantReply() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "sk-test-key");

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-key"))
                .andExpect(jsonPath("$.model").value("llama-3.3-70b-versatile"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hola"))
                .andRespond(withSuccess("""
                        {
                          "choices": [{"message": {"role": "assistant", "content": "Hola, ¿en qué te ayudo?"}}],
                          "usage": {"prompt_tokens": 120, "completion_tokens": 15}
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatCompletionResult result = aiChatClient.complete(
                provider,
                List.of(ChatMessage.system("contexto"), ChatMessage.user("hola"))
        );

        Assertions.assertEquals("Hola, ¿en qué te ayudo?", result.content());
        Assertions.assertEquals(120, result.promptTokens());
        Assertions.assertEquals(15, result.completionTokens());
        mockServer.verify();
    }

    @Test
    void completeShouldSendVisionArrayOfPartsContentWhenMessageCarriesAnImageUrl() {
        ResolvedAiProvider provider = buildProvider("https://integrate.api.nvidia.com/v1", "nvidia/nemotron-nano-12b-v2-vl", "key");

        mockServer.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content").value("contexto"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[0].text").value("Extraé los datos de este recibo."))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value("data:image/jpeg;base64,abc123"))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant", "content": "ok"}}]}
                        """, MediaType.APPLICATION_JSON));

        aiChatClient.complete(
                provider,
                List.of(
                        ChatMessage.system("contexto"),
                        ChatMessage.userWithImage("Extraé los datos de este recibo.", "data:image/jpeg;base64,abc123")
                )
        );

        mockServer.verify();
    }

    @Test
    void completeShouldDisableThinkingModeForNvidiaProvider() {
        ResolvedAiProvider provider = new ResolvedAiProvider(
                "nvidia", "https://integrate.api.nvidia.com/v1", "key", "nvidia/nemotron-3-nano-30b-a3b", null);

        mockServer.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.chat_template_kwargs.thinking").value(false))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant", "content": "ok"}}]}
                        """, MediaType.APPLICATION_JSON));

        aiChatClient.complete(provider, List.of(ChatMessage.user("hola")));

        mockServer.verify();
    }

    @Test
    void completeShouldNotSendChatTemplateKwargsForNonNvidiaProviders() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "key");

        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(jsonPath("$.chat_template_kwargs").doesNotExist())
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant", "content": "ok"}}]}
                        """, MediaType.APPLICATION_JSON));

        aiChatClient.complete(provider, List.of(ChatMessage.user("hola")));

        mockServer.verify();
    }

    @Test
    void completeShouldStripTrailingSlashFromBaseUrlBeforeAppendingPath() {
        ResolvedAiProvider provider = buildProvider("https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.5-flash", "key");

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant", "content": "ok"}}]}
                        """, MediaType.APPLICATION_JSON));

        ChatCompletionResult result = aiChatClient.complete(provider, List.of(ChatMessage.user("hola")));

        Assertions.assertEquals("ok", result.content());
        mockServer.verify();
    }

    @Test
    void completeShouldThrowAuthExceptionOn401() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "bad-key");
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withUnauthorizedRequest());

        AiProviderAuthException ex = Assertions.assertThrows(AiProviderAuthException.class, () ->
                aiChatClient.complete(provider, List.of(ChatMessage.user("hola")))
        );
        Assertions.assertEquals("groq", ex.getProviderName());
        Assertions.assertTrue(ex.getMessage().contains("API key"));
    }

    @Test
    void completeShouldThrowRateLimitExceptionOn429() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "key");
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        AiProviderRateLimitException ex = Assertions.assertThrows(AiProviderRateLimitException.class, () ->
                aiChatClient.complete(provider, List.of(ChatMessage.user("hola")))
        );
        Assertions.assertTrue(ex.getMessage().contains("límite de uso"));
    }

    @Test
    void completeShouldThrowModelNotFoundExceptionOn404() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "modelo-inexistente", "key");
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        AiProviderModelNotFoundException ex = Assertions.assertThrows(AiProviderModelNotFoundException.class, () ->
                aiChatClient.complete(provider, List.of(ChatMessage.user("hola")))
        );
        Assertions.assertTrue(ex.getMessage().contains("modelo"));
    }

    @Test
    void completeShouldThrowModelNotFoundExceptionWhenErrorBodyMentionsModel() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "modelo-inexistente", "key");
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"The model `modelo-inexistente` does not exist\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Assertions.assertThrows(AiProviderModelNotFoundException.class, () ->
                aiChatClient.complete(provider, List.of(ChatMessage.user("hola")))
        );
    }

    @Test
    void completeShouldThrowUnavailableExceptionWhenResponseHasNoChoices() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "key");
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices": []}
                        """, MediaType.APPLICATION_JSON));

        AiProviderUnavailableException ex = Assertions.assertThrows(AiProviderUnavailableException.class, () ->
                aiChatClient.complete(provider, List.of(ChatMessage.user("hola")))
        );
        Assertions.assertEquals("groq", ex.getProviderName());
    }

    @Test
    void completeShouldThrowUnavailableExceptionOn5xx() {
        ResolvedAiProvider provider = buildProvider("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "key");
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withServerError());

        AiProviderUnavailableException ex = Assertions.assertThrows(AiProviderUnavailableException.class, () ->
                aiChatClient.complete(provider, List.of(ChatMessage.user("hola")))
        );
        Assertions.assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Test
    void mapAccessExceptionShouldReturnTimeoutExceptionForSocketTimeout() {
        ResourceAccessException timeoutException = new ResourceAccessException("timed out", new SocketTimeoutException());

        AiProviderException mapped = aiChatClient.mapAccessException("groq", timeoutException);

        Assertions.assertInstanceOf(AiProviderTimeoutException.class, mapped);
        Assertions.assertEquals("groq", mapped.getProviderName());
    }

    @Test
    void mapAccessExceptionShouldReturnUnavailableExceptionForOtherConnectivityFailures() {
        ResourceAccessException connectException = new ResourceAccessException("connection refused", new ConnectException());

        AiProviderException mapped = aiChatClient.mapAccessException("groq", connectException);

        Assertions.assertInstanceOf(AiProviderUnavailableException.class, mapped);
    }

    @Test
    void normalizeBaseUrlShouldStripSingleTrailingSlash() {
        Assertions.assertEquals("https://api.groq.com/openai/v1", AiChatClient.normalizeBaseUrl("https://api.groq.com/openai/v1/"));
        Assertions.assertEquals("https://api.groq.com/openai/v1", AiChatClient.normalizeBaseUrl("https://api.groq.com/openai/v1"));
        Assertions.assertEquals("", AiChatClient.normalizeBaseUrl(null));
    }

    private static ResolvedAiProvider buildProvider(String baseUrl, String model, String apiKey) {
        return new ResolvedAiProvider("groq", baseUrl, apiKey, model, null);
    }
}
