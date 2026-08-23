package com.korofin.backend.controller.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.ai.AiUsageResponse;
import com.korofin.backend.dto.ai.ChatMessageResponse;
import com.korofin.backend.dto.ai.ChatReplyResponse;
import com.korofin.backend.dto.ai.ChatRequest;
import com.korofin.backend.entity.ai.AiMessageRole;
import com.korofin.backend.exception.ai.AiMessageQuotaExceededException;
import com.korofin.backend.exception.ai.AiProviderNotConfiguredException;
import com.korofin.backend.exception.ai.AiProviderRateLimitException;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.ai.AiChatService;
import com.korofin.backend.service.ai.provider.AiChatOrchestrator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
@Import(SecurityConfig.class)
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private AiChatService aiChatService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String AUTH_HEADER = "Bearer test-token";

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void chatReturns200WithAssistantReply() throws Exception {
        ChatRequest request = new ChatRequest("¿cómo voy este mes?");
        when(aiChatService.chat(eq(request))).thenReturn(
                new ChatReplyResponse("Vas bien este mes", "groq", "llama-3.3", Instant.parse("2026-07-03T10:00:00Z"))
        );

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Vas bien este mes"))
                .andExpect(jsonPath("$.providerName").value("groq"));
    }

    @Test
    void chatReturns400WhenMessageIsBlank() throws Exception {
        String invalidBody = """
                {"message": ""}
                """;

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatReturns503WhenNoProviderIsConfigured() throws Exception {
        ChatRequest request = new ChatRequest("hola");
        when(aiChatService.chat(eq(request))).thenThrow(
                new AiProviderNotConfiguredException(AiChatOrchestrator.GENERIC_MESSAGE)
        );

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(AiChatOrchestrator.GENERIC_MESSAGE));
    }

    @Test
    void chatReturns429WhenProviderRateLimited() throws Exception {
        ChatRequest request = new ChatRequest("hola");
        when(aiChatService.chat(eq(request))).thenThrow(new AiProviderRateLimitException("groq"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("El proveedor de IA alcanzó su límite de uso. Intenta de nuevo en unos minutos."));
    }

    @Test
    void chatReturns429WhenMonthlyQuotaExceeded() throws Exception {
        ChatRequest request = new ChatRequest("hola");
        when(aiChatService.chat(eq(request))).thenThrow(new AiMessageQuotaExceededException(
                "Alcanzaste el límite de 5 mensajes de IA este mes. Tu límite se reinicia el 1 de agosto de 2026."
        ));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "Alcanzaste el límite de 5 mensajes de IA este mes. Tu límite se reinicia el 1 de agosto de 2026."
                ));
    }

    @Test
    void getUsageReturns200WithUsageShape() throws Exception {
        AiUsageResponse usage = new AiUsageResponse(3, 5, 2, Instant.parse("2026-08-01T00:00:00Z"));
        when(aiChatService.getUsage()).thenReturn(usage);

        mockMvc.perform(get("/api/ai/chat/usage").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(3))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.remaining").value(2))
                .andExpect(jsonPath("$.resetsAt").value("2026-08-01T00:00:00Z"));
    }

    @Test
    void getHistoryReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        ChatMessageResponse message = new ChatMessageResponse(
                1L, AiMessageRole.USER, "hola", null, null, Instant.parse("2026-07-03T10:00:00Z")
        );
        when(aiChatService.getHistory(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(message), pageable, 1));

        mockMvc.perform(get("/api/ai/chat/history").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("hola"));
    }

    @Test
    void chatReturns403WithoutAuthToken() throws Exception {
        ChatRequest request = new ChatRequest("hola");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
