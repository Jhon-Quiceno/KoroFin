package com.korofin.backend.service.ai;

import com.korofin.backend.dto.ai.AiUsageResponse;
import com.korofin.backend.dto.ai.ChatMessageResponse;
import com.korofin.backend.dto.ai.ChatReplyResponse;
import com.korofin.backend.dto.ai.ChatRequest;
import com.korofin.backend.entity.ai.AiMessage;
import com.korofin.backend.entity.ai.AiMessageKind;
import com.korofin.backend.entity.ai.AiMessageRole;
import com.korofin.backend.entity.ai.AiUsageEventType;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.ai.AiMessageQuotaExceededException;
import com.korofin.backend.exception.ai.AiProviderNotConfiguredException;
import com.korofin.backend.mapper.ai.AiMessageMapper;
import com.korofin.backend.repository.ai.AiMessageRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.service.ai.provider.AiCallContext;
import com.korofin.backend.service.ai.provider.AiChatOrchestrator;
import com.korofin.backend.service.ai.provider.AiProviderProperties;
import com.korofin.backend.service.ai.provider.ChatCompletionResult;
import com.korofin.backend.service.ai.provider.ChatMessage;
import com.korofin.backend.service.ai.provider.FinancialContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    /** "Ahora" fijo para que la ventana de la cuota mensual sea determinista: julio 2026 UTC. */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC);
    private static final String CURRENT_PERIOD = "2026-07";

    @Mock
    private AiMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiChatOrchestrator aiChatOrchestrator;

    @Mock
    private FinancialContextBuilder contextBuilder;

    @Mock
    private AiMessageMapper aiMessageMapper;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        AiProviderProperties aiProviderProperties = new AiProviderProperties();
        aiProviderProperties.setMonthlyMessageLimit(5);
        aiChatService = new AiChatService(
                messageRepository, userRepository, aiChatOrchestrator, contextBuilder, aiMessageMapper,
                aiProviderProperties, FIXED_CLOCK
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatShouldThrowAiProviderNotConfiguredExceptionWhenNoProviderIsConfigured() {
        setAuthenticatedUser(1L);
        when(userRepository.reserveAiChatQuota(1L, CURRENT_PERIOD, 5)).thenReturn(1);
        when(contextBuilder.buildSystemPrompt()).thenReturn("contexto");
        when(messageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(eq(1L), eq(AiMessageKind.CHAT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenThrow(new AiProviderNotConfiguredException(AiChatOrchestrator.GENERIC_MESSAGE));

        ChatRequest request = new ChatRequest("¿cómo voy este mes?");

        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> aiChatService.chat(request));
        verify(messageRepository, never()).save(any(AiMessage.class));
        verify(userRepository, never()).releaseAiChatQuota(any(), any());
    }

    @Test
    void chatShouldPersistBothUserAndAssistantMessagesAndReturnReply() {
        setAuthenticatedUser(2L);
        when(userRepository.reserveAiChatQuota(2L, CURRENT_PERIOD, 5)).thenReturn(1);
        when(contextBuilder.buildSystemPrompt()).thenReturn("contexto financiero");
        when(messageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(eq(2L), eq(AiMessageKind.CHAT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userRepository.getReferenceById(2L)).thenReturn(buildUser(2L));
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult("Vas bien este mes", "groq", "llama-3.3", 100, 20));
        when(messageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> {
            AiMessage message = invocation.getArgument(0);
            message.setId(message.getRole() == AiMessageRole.USER ? 1L : 2L);
            message.setCreatedAt(Instant.parse("2026-07-03T10:00:00Z"));
            return message;
        });

        ChatReplyResponse response = aiChatService.chat(new ChatRequest("¿cómo voy este mes?"));

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<AiMessage> savedMessages = captor.getAllValues();

        AiMessage userMessage = savedMessages.get(0);
        Assertions.assertEquals(AiMessageRole.USER, userMessage.getRole());
        Assertions.assertEquals(AiMessageKind.CHAT, userMessage.getKind());
        Assertions.assertEquals("¿cómo voy este mes?", userMessage.getContent());
        Assertions.assertNull(userMessage.getProviderName());

        AiMessage assistantMessage = savedMessages.get(1);
        Assertions.assertEquals(AiMessageRole.ASSISTANT, assistantMessage.getRole());
        Assertions.assertEquals("Vas bien este mes", assistantMessage.getContent());
        Assertions.assertEquals("groq", assistantMessage.getProviderName());
        Assertions.assertEquals("llama-3.3", assistantMessage.getModel());

        Assertions.assertEquals("Vas bien este mes", response.reply());
        Assertions.assertEquals("groq", response.providerName());
        Assertions.assertEquals("llama-3.3", response.model());

        ArgumentCaptor<AiCallContext> ctxCaptor = ArgumentCaptor.forClass(AiCallContext.class);
        verify(aiChatOrchestrator).complete(anyList(), ctxCaptor.capture());
        Assertions.assertEquals(new AiCallContext(2L, AiUsageEventType.CHAT), ctxCaptor.getValue());
    }

    @Test
    void chatShouldSendSystemPromptFirstFollowedByChronologicalHistoryThenNewMessage() {
        setAuthenticatedUser(3L);
        when(userRepository.reserveAiChatQuota(3L, CURRENT_PERIOD, 5)).thenReturn(1);
        when(contextBuilder.buildSystemPrompt()).thenReturn("contexto");

        AiMessage olderAssistantTurn = new AiMessage();
        olderAssistantTurn.setRole(AiMessageRole.ASSISTANT);
        olderAssistantTurn.setContent("respuesta anterior");
        AiMessage newerUserTurn = new AiMessage();
        newerUserTurn.setRole(AiMessageRole.USER);
        newerUserTurn.setContent("pregunta anterior");
        when(messageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(eq(3L), eq(AiMessageKind.CHAT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newerUserTurn, olderAssistantTurn)));
        when(userRepository.getReferenceById(3L)).thenReturn(buildUser(3L));
        when(messageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<ChatMessage>> conversationCaptor = ArgumentCaptor.forClass(List.class);
        when(aiChatOrchestrator.complete(conversationCaptor.capture(), any()))
                .thenReturn(new ChatCompletionResult("ok", "groq", "llama-3.3", null, null));

        aiChatService.chat(new ChatRequest("pregunta nueva"));

        List<ChatMessage> conversation = conversationCaptor.getValue();
        Assertions.assertEquals(ChatMessage.ROLE_SYSTEM, conversation.get(0).role());
        Assertions.assertEquals("contexto", conversation.get(0).content());
        Assertions.assertEquals("respuesta anterior", conversation.get(1).content());
        Assertions.assertEquals("pregunta anterior", conversation.get(2).content());
        Assertions.assertEquals("pregunta nueva", conversation.get(3).content());
        Assertions.assertEquals(4, conversation.size());
    }

    @Test
    void getHistoryShouldReturnMappedPageForCurrentUserFilteredByChatKind() {
        setAuthenticatedUser(4L);
        Pageable pageable = PageRequest.of(0, 20);
        AiMessage message = new AiMessage();
        message.setId(5L);
        Page<AiMessage> page = new PageImpl<>(List.of(message), pageable, 1);
        when(messageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(4L, AiMessageKind.CHAT, pageable)).thenReturn(page);
        when(aiMessageMapper.toChatResponse(message)).thenReturn(
                new ChatMessageResponse(5L, AiMessageRole.USER, "hola", null, null, Instant.now())
        );

        Page<ChatMessageResponse> result = aiChatService.getHistory(pageable);

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(5L, result.getContent().get(0).id());
    }

    @Test
    void chatShouldThrowAiMessageQuotaExceededExceptionWhenMonthlyLimitReached() {
        setAuthenticatedUser(5L);
        when(userRepository.reserveAiChatQuota(5L, CURRENT_PERIOD, 5)).thenReturn(0);
        when(userRepository.findById(5L)).thenReturn(Optional.of(buildUser(5L, 5, CURRENT_PERIOD)));

        ChatRequest request = new ChatRequest("hola");

        AiMessageQuotaExceededException ex = Assertions.assertThrows(
                AiMessageQuotaExceededException.class, () -> aiChatService.chat(request)
        );
        Assertions.assertEquals(
                "Alcanzaste el límite de 5 mensajes de IA este mes. Tu límite se reinicia el 1 de agosto de 2026.",
                ex.getMessage()
        );
        verify(aiChatOrchestrator, never()).complete(anyList(), any());
        verify(messageRepository, never()).save(any(AiMessage.class));
    }

    @Test
    void chatShouldProceedWhenUsageIsBelowMonthlyLimit() {
        setAuthenticatedUser(6L);
        when(userRepository.reserveAiChatQuota(6L, CURRENT_PERIOD, 5)).thenReturn(1);
        when(contextBuilder.buildSystemPrompt()).thenReturn("contexto");
        when(messageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(eq(6L), eq(AiMessageKind.CHAT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userRepository.getReferenceById(6L)).thenReturn(buildUser(6L));
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult("ok", "groq", "llama-3.3", null, null));
        when(messageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Assertions.assertDoesNotThrow(() -> aiChatService.chat(new ChatRequest("hola")));
        verify(aiChatOrchestrator).complete(anyList(), any());
    }

    @Test
    void getUsageShouldReturnUsedLimitRemainingAndResetsAt() {
        setAuthenticatedUser(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(buildUser(7L, 3, CURRENT_PERIOD)));

        AiUsageResponse usage = aiChatService.getUsage();

        Assertions.assertEquals(3, usage.used());
        Assertions.assertEquals(5, usage.limit());
        Assertions.assertEquals(2, usage.remaining());
        Assertions.assertEquals(Instant.parse("2026-08-01T00:00:00Z"), usage.resetsAt());
    }

    @Test
    void getUsageShouldClampRemainingToZeroWhenUsedExceedsLimit() {
        setAuthenticatedUser(8L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(buildUser(8L, 9, CURRENT_PERIOD)));

        AiUsageResponse usage = aiChatService.getUsage();

        Assertions.assertEquals(0, usage.remaining());
    }

    @Test
    void getUsageShouldTreatMismatchedPeriodAsReset() {
        setAuthenticatedUser(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(buildUser(10L, 5, "2026-06")));

        AiUsageResponse usage = aiChatService.getUsage();

        Assertions.assertEquals(0, usage.used());
        Assertions.assertEquals(5, usage.remaining());
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }

    private User buildUser(Long userId) {
        return buildUser(userId, 0, null);
    }

    private User buildUser(Long userId, int aiChatUsed, String aiChatPeriod) {
        User user = new User();
        user.setId(userId);
        user.setAiChatUsed(aiChatUsed);
        user.setAiChatPeriod(aiChatPeriod);
        return user;
    }
}
