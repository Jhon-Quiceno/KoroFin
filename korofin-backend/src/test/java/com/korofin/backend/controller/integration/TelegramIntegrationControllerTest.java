package com.korofin.backend.controller.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.integration.TelegramConfirmLinkRequest;
import com.korofin.backend.dto.integration.TelegramLinkCodeResponse;
import com.korofin.backend.dto.integration.TelegramMessageRequest;
import com.korofin.backend.dto.integration.TelegramReceiptRequest;
import com.korofin.backend.dto.integration.TelegramReplyResponse;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.integration.telegram.TelegramExpenseService;
import com.korofin.backend.service.integration.telegram.TelegramLinkService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelegramIntegrationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.telegram.webhook-secret=test-webhook-secret")
class TelegramIntegrationControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";
    private static final String WEBHOOK_HEADER = "X-Telegram-Webhook-Secret";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TelegramLinkService telegramLinkService;

    @MockitoBean
    private TelegramExpenseService telegramExpenseService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void generateLinkCodeReturns200WithJwt() throws Exception {
        when(telegramLinkService.generateLinkCode()).thenReturn(new TelegramLinkCodeResponse("123456", 300));

        mockMvc.perform(post("/api/integrations/telegram/link-code").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("123456"));
    }

    @Test
    void generateLinkCodeReturns403WithoutJwt() throws Exception {
        mockMvc.perform(post("/api/integrations/telegram/link-code"))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmLinkReturns200WithValidWebhookSecret() throws Exception {
        TelegramConfirmLinkRequest request = new TelegramConfirmLinkRequest("123456", 555L);

        mockMvc.perform(post("/api/integrations/telegram/confirm-link")
                        .header(WEBHOOK_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void confirmLinkReturns401WithoutWebhookSecret() throws Exception {
        TelegramConfirmLinkRequest request = new TelegramConfirmLinkRequest("123456", 555L);

        mockMvc.perform(post("/api/integrations/telegram/confirm-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerExpenseFromMessageReturns200WithValidWebhookSecret() throws Exception {
        TelegramMessageRequest request = new TelegramMessageRequest(555L, "Uber 15000");
        when(telegramExpenseService.registerFromMessage(555L, "Uber 15000"))
                .thenReturn(new TelegramReplyResponse("Registré un gasto de $15000."));

        mockMvc.perform(post("/api/integrations/telegram/expenses")
                        .header(WEBHOOK_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registré un gasto de $15000."));
    }

    @Test
    void registerExpenseFromReceiptReturns200WithValidWebhookSecret() throws Exception {
        TelegramReceiptRequest request = new TelegramReceiptRequest(555L, "data:image/jpeg;base64,AAAA");
        when(telegramExpenseService.registerFromReceipt(555L, "data:image/jpeg;base64,AAAA"))
                .thenReturn(new TelegramReplyResponse("Registré un gasto de $45000."));

        mockMvc.perform(post("/api/integrations/telegram/receipts")
                        .header(WEBHOOK_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void registerExpenseFromMessageReturns401WithWrongWebhookSecret() throws Exception {
        TelegramMessageRequest request = new TelegramMessageRequest(555L, "Uber 15000");

        mockMvc.perform(post("/api/integrations/telegram/expenses")
                        .header(WEBHOOK_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
