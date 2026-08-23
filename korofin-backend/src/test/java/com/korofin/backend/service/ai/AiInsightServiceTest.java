package com.korofin.backend.service.ai;

import com.korofin.backend.dto.ai.InsightResponse;
import com.korofin.backend.entity.ai.AiMessage;
import com.korofin.backend.entity.ai.AiMessageKind;
import com.korofin.backend.entity.ai.AiMessageRole;
import com.korofin.backend.entity.ai.AiUsageEventType;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.ai.AiProviderNotConfiguredException;
import com.korofin.backend.mapper.ai.AiMessageMapper;
import com.korofin.backend.repository.ai.AiMessageRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.service.ai.provider.AiCallContext;
import com.korofin.backend.service.ai.provider.AiChatOrchestrator;
import com.korofin.backend.service.ai.provider.ChatCompletionResult;
import com.korofin.backend.service.ai.provider.FinancialContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

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

    @InjectMocks
    private AiInsightService aiInsightService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLatestInsightShouldReturnNullWhenNoneExistsYet() {
        setAuthenticatedUser(1L);
        when(messageRepository.findFirstByUser_IdAndKindOrderByCreatedAtDesc(1L, AiMessageKind.INSIGHT))
                .thenReturn(Optional.empty());

        InsightResponse response = aiInsightService.getLatestInsight();

        Assertions.assertNull(response);
    }

    @Test
    void getLatestInsightShouldReturnMappedInsightWhenOneExists() {
        setAuthenticatedUser(2L);
        AiMessage insight = new AiMessage();
        insight.setId(10L);
        when(messageRepository.findFirstByUser_IdAndKindOrderByCreatedAtDesc(2L, AiMessageKind.INSIGHT))
                .thenReturn(Optional.of(insight));
        when(aiMessageMapper.toInsightResponse(insight)).thenReturn(
                new InsightResponse(10L, "- Ahorra más", "groq", "llama-3.3", null)
        );

        InsightResponse response = aiInsightService.getLatestInsight();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(10L, response.id());
    }

    @Test
    void generateInsightShouldThrowWhenNoProviderIsConfigured() {
        setAuthenticatedUser(3L);
        when(contextBuilder.buildSystemPrompt()).thenReturn("contexto");
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenThrow(new AiProviderNotConfiguredException(AiChatOrchestrator.GENERIC_MESSAGE));

        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> aiInsightService.generateInsight());
        verify(messageRepository, never()).save(any(AiMessage.class));
    }

    @Test
    void generateInsightShouldPersistAssistantInsightMessageWithProviderAndModel() {
        setAuthenticatedUser(4L);
        when(contextBuilder.buildSystemPrompt()).thenReturn("contexto financiero");
        when(userRepository.getReferenceById(4L)).thenReturn(buildUser(4L));
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult("- Reduce gastos en comida\n- Aumenta tu ahorro", "groq", "llama-3.3", null, null));
        when(messageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiMessageMapper.toInsightResponse(any(AiMessage.class))).thenAnswer(invocation -> {
            AiMessage saved = invocation.getArgument(0);
            return new InsightResponse(saved.getId(), saved.getContent(), saved.getProviderName(), saved.getModel(), saved.getCreatedAt());
        });

        InsightResponse response = aiInsightService.generateInsight();

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageRepository).save(captor.capture());
        AiMessage saved = captor.getValue();
        Assertions.assertEquals(AiMessageRole.ASSISTANT, saved.getRole());
        Assertions.assertEquals(AiMessageKind.INSIGHT, saved.getKind());
        Assertions.assertEquals("groq", saved.getProviderName());
        Assertions.assertEquals("llama-3.3", saved.getModel());
        Assertions.assertEquals("- Reduce gastos en comida\n- Aumenta tu ahorro", response.content());
        ArgumentCaptor<AiCallContext> ctxCaptor = ArgumentCaptor.forClass(AiCallContext.class);
        verify(aiChatOrchestrator).complete(anyList(), ctxCaptor.capture());
        Assertions.assertEquals(new AiCallContext(4L, AiUsageEventType.INSIGHT), ctxCaptor.getValue());
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }

    private User buildUser(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
