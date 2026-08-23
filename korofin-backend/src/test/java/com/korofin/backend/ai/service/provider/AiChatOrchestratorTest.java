package com.korofin.backend.ai.service.provider;

import com.korofin.backend.ai.entity.AiUsageEventType;
import com.korofin.backend.ai.exception.AiProviderNotConfiguredException;
import com.korofin.backend.ai.exception.AiProviderTimeoutException;
import com.korofin.backend.ai.exception.AiProviderUnavailableException;
import com.korofin.backend.ai.exception.AiProvidersExhaustedException;
import com.korofin.backend.ai.service.AiUsageEventService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatOrchestratorTest {

    @Mock
    private AiProviderRegistry registry;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private AiUsageEventService aiUsageEventService;

    @Mock
    private AiProviderPricing aiProviderPricing;

    @InjectMocks
    private AiChatOrchestrator orchestrator;

    private static final ResolvedAiProvider NVIDIA = new ResolvedAiProvider(
            "nvidia", "https://integrate.api.nvidia.com/v1", "sk-nvidia", "meta/llama-3.1-70b-instruct", "nvidia/nemotron-nano-12b-v2-vl");
    private static final ResolvedAiProvider OPENCODE = new ResolvedAiProvider(
            "opencode", "https://opencode.ai/zen/v1", "sk-opencode", "deepseek-v4-flash-free", null);
    private static final ResolvedAiProvider NVIDIA_VISION = new ResolvedAiProvider(
            "nvidia", "https://integrate.api.nvidia.com/v1", "sk-nvidia", "nvidia/nemotron-nano-12b-v2-vl", "nvidia/nemotron-nano-12b-v2-vl");
    private static final ResolvedAiProvider OPENROUTER_VISION = new ResolvedAiProvider(
            "openrouter", "https://openrouter.ai/api/v1", "sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", "nvidia/nemotron-nano-12b-v2-vl:free");
    private static final ResolvedAiProvider OPENROUTER_VISION_SWAPPED = new ResolvedAiProvider(
            "openrouter", "https://openrouter.ai/api/v1", "sk-openrouter", "nvidia/nemotron-nano-12b-v2-vl:free", "nvidia/nemotron-nano-12b-v2-vl:free");

    @Test
    void completeShouldReturnSecondProvidersResultWhenFirstProviderFails() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA, OPENCODE));
        when(aiChatClient.complete(eq(NVIDIA), anyList())).thenThrow(new AiProviderUnavailableException("nvidia"));
        when(aiChatClient.complete(eq(OPENCODE), anyList()))
                .thenReturn(new ChatCompletionResult("Hola desde opencode", null, null, null, null));

        ChatCompletionResult result = orchestrator.complete(List.of(ChatMessage.user("hola")));

        Assertions.assertEquals("Hola desde opencode", result.content());
        Assertions.assertEquals("opencode", result.providerName());
        Assertions.assertEquals("deepseek-v4-flash-free", result.model());
        verifyNoInteractions(aiUsageEventService, aiProviderPricing);
    }

    @Test
    void completeShouldThrowAiProvidersExhaustedExceptionWhenEveryConfiguredProviderFails() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA, OPENCODE));
        when(aiChatClient.complete(eq(NVIDIA), anyList())).thenThrow(new AiProviderUnavailableException("nvidia"));
        when(aiChatClient.complete(eq(OPENCODE), anyList())).thenThrow(new AiProviderUnavailableException("opencode"));

        List<ChatMessage> messages = List.of(ChatMessage.user("hola"));
        Assertions.assertThrows(AiProvidersExhaustedException.class, () -> orchestrator.complete(messages));
    }

    @Test
    void completeShouldThrowAiProviderNotConfiguredExceptionAndNeverCallClientWhenNoProviderIsEnabled() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of());

        List<ChatMessage> messages = List.of(ChatMessage.user("hola"));
        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> orchestrator.complete(messages));
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void completeWithContextShouldRecordAttemptTelemetryForBothTheFailedAndTheWinningProvider() {
        when(registry.enabledInPriorityOrder(AiUsageEventType.CHAT)).thenReturn(List.of(NVIDIA, OPENCODE));
        when(aiChatClient.complete(eq(NVIDIA), anyList())).thenThrow(new AiProviderTimeoutException("nvidia"));
        when(aiChatClient.complete(eq(OPENCODE), anyList()))
                .thenReturn(new ChatCompletionResult("Hola desde opencode", null, null, 10, 5));
        when(aiProviderPricing.estimateCost("opencode", "deepseek-v4-flash-free", 15)).thenReturn(BigDecimal.ZERO);

        AiCallContext ctx = new AiCallContext(42L, AiUsageEventType.CHAT);
        ChatCompletionResult result = orchestrator.complete(List.of(ChatMessage.user("hola")), ctx);

        Assertions.assertEquals("opencode", result.providerName());
        verify(aiUsageEventService).recordAttempt(
                eq(42L), eq("nvidia"), eq(AiUsageEventType.CHAT), eq(0), isNull(), anyInt(), eq(false), eq("AiProviderTimeoutException")
        );
        verify(aiUsageEventService).recordAttempt(
                eq(42L), eq("opencode"), eq(AiUsageEventType.CHAT), eq(15), eq(BigDecimal.ZERO), anyInt(), eq(true), isNull()
        );
    }

    @Test
    void completeWithContextShouldNotRecordAnyAttemptWhenNoProviderIsEnabled() {
        when(registry.enabledInPriorityOrder(AiUsageEventType.CHAT)).thenReturn(List.of());

        List<ChatMessage> messages = List.of(ChatMessage.user("hola"));
        AiCallContext ctx = new AiCallContext(1L, AiUsageEventType.CHAT);
        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> orchestrator.complete(messages, ctx));
        verifyNoInteractions(aiUsageEventService, aiProviderPricing);
    }

    @Test
    void completeWithContextShouldUseThePerTaskProviderOrderInsteadOfTheGlobalOne() {
        when(registry.enabledInPriorityOrder(AiUsageEventType.INSIGHT)).thenReturn(List.of(OPENCODE));
        when(aiChatClient.complete(eq(OPENCODE), anyList()))
                .thenReturn(new ChatCompletionResult("Resumen financiero", null, null, 10, 5));
        when(aiProviderPricing.estimateCost("opencode", "deepseek-v4-flash-free", 15)).thenReturn(BigDecimal.ZERO);

        AiCallContext ctx = new AiCallContext(99L, AiUsageEventType.INSIGHT);
        ChatCompletionResult result = orchestrator.complete(List.of(ChatMessage.user("dame un resumen")), ctx);

        Assertions.assertEquals("opencode", result.providerName());
        verify(registry).enabledInPriorityOrder(AiUsageEventType.INSIGHT);
        verify(registry, never()).enabledInPriorityOrder();
    }

    @Test
    void completeVisionShouldSkipProvidersWithNoVisionModelAndCallTheOneThatHasIt() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(OPENCODE, NVIDIA));
        when(aiChatClient.complete(eq(NVIDIA_VISION), anyList()))
                .thenReturn(new ChatCompletionResult("{\"isReceipt\":true}", null, null, 10, 5));

        List<ChatMessage> messages = List.of(
                ChatMessage.system("contexto"),
                ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc")
        );
        ChatCompletionResult result = orchestrator.completeVision(messages);

        Assertions.assertEquals("nvidia", result.providerName());
        Assertions.assertEquals("nvidia/nemotron-nano-12b-v2-vl", result.model());
        verify(aiChatClient, never()).complete(eq(OPENCODE), anyList());
    }

    @Test
    void completeVisionShouldFailOverFromNvidiaToOpenRouterWhenNvidiaFails() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA, OPENROUTER_VISION));
        when(aiChatClient.complete(eq(NVIDIA_VISION), anyList())).thenThrow(new AiProviderUnavailableException("nvidia"));
        when(aiChatClient.complete(eq(OPENROUTER_VISION_SWAPPED), anyList()))
                .thenReturn(new ChatCompletionResult("{\"isReceipt\":true}", null, null, 10, 5));

        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        ChatCompletionResult result = orchestrator.completeVision(messages);

        Assertions.assertEquals("openrouter", result.providerName());
        Assertions.assertEquals("nvidia/nemotron-nano-12b-v2-vl:free", result.model());
    }

    @Test
    void completeVisionShouldNeverSendNvidiasTextModelForAVisionTurn() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA));
        when(aiChatClient.complete(eq(NVIDIA_VISION), anyList()))
                .thenReturn(new ChatCompletionResult("{\"isReceipt\":true}", null, null, 10, 5));

        orchestrator.completeVision(List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc")));

        verify(aiChatClient, never()).complete(eq(NVIDIA), anyList());
    }

    @Test
    void completeVisionShouldThrowAiProviderNotConfiguredExceptionAndNeverCallClientWhenNvidiaIsNotEnabled() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(OPENCODE));

        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> orchestrator.completeVision(messages));
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void completeVisionShouldThrowAiProviderNotConfiguredExceptionWhenNvidiaHasNoVisionModelConfigured() {
        ResolvedAiProvider nvidiaWithoutVision = new ResolvedAiProvider(
                "nvidia", "https://integrate.api.nvidia.com/v1", "sk-nvidia", "meta/llama-3.1-70b-instruct", null);
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(nvidiaWithoutVision));

        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> orchestrator.completeVision(messages));
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void completeVisionShouldThrowAiProviderNotConfiguredExceptionWhenNoEnabledProviderHasAnyVisionModelAtAll() {
        ResolvedAiProvider nvidiaWithoutVision = new ResolvedAiProvider(
                "nvidia", "https://integrate.api.nvidia.com/v1", "sk-nvidia", "meta/llama-3.1-70b-instruct", null);
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(nvidiaWithoutVision, OPENCODE));

        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        Assertions.assertThrows(AiProviderNotConfiguredException.class, () -> orchestrator.completeVision(messages));
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void completeVisionShouldPropagateExceptionWhenEveryVisionCapableProviderFails() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA, OPENROUTER_VISION));
        when(aiChatClient.complete(eq(NVIDIA_VISION), anyList())).thenThrow(new AiProviderUnavailableException("nvidia"));
        when(aiChatClient.complete(eq(OPENROUTER_VISION_SWAPPED), anyList())).thenThrow(new AiProviderUnavailableException("openrouter"));

        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        Assertions.assertThrows(AiProvidersExhaustedException.class, () -> orchestrator.completeVision(messages));
    }

    @Test
    void completeVisionShouldPropagateNvidiaFailureWithoutTryingAnyNonVisionProvider() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA, OPENCODE));
        when(aiChatClient.complete(eq(NVIDIA_VISION), anyList())).thenThrow(new AiProviderUnavailableException("nvidia"));

        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        Assertions.assertThrows(AiProvidersExhaustedException.class, () -> orchestrator.completeVision(messages));
        verify(aiChatClient, never()).complete(eq(OPENCODE), anyList());
    }

    @Test
    void completeVisionWithContextShouldRecordAttemptTelemetryForBothTheFailedAndTheWinningProvider() {
        when(registry.enabledInPriorityOrder()).thenReturn(List.of(NVIDIA, OPENROUTER_VISION));
        when(aiChatClient.complete(eq(NVIDIA_VISION), anyList())).thenThrow(new AiProviderTimeoutException("nvidia"));
        when(aiChatClient.complete(eq(OPENROUTER_VISION_SWAPPED), anyList()))
                .thenReturn(new ChatCompletionResult("{\"isReceipt\":true}", null, null, 10, 5));
        when(aiProviderPricing.estimateCost("openrouter", "nvidia/nemotron-nano-12b-v2-vl:free", 15)).thenReturn(BigDecimal.ZERO);

        AiCallContext ctx = new AiCallContext(7L, AiUsageEventType.CATEGORIZE);
        List<ChatMessage> messages = List.of(ChatMessage.userWithImage("Extraé los datos.", "data:image/jpeg;base64,abc"));
        ChatCompletionResult result = orchestrator.completeVision(messages, ctx);

        Assertions.assertEquals("openrouter", result.providerName());
        verify(aiUsageEventService).recordAttempt(
                eq(7L), eq("nvidia"), eq(AiUsageEventType.CATEGORIZE), eq(0), isNull(), anyInt(), eq(false), eq("AiProviderTimeoutException")
        );
        verify(aiUsageEventService).recordAttempt(
                eq(7L), eq("openrouter"), eq(AiUsageEventType.CATEGORIZE), eq(15), eq(BigDecimal.ZERO), anyInt(), eq(true), isNull()
        );
    }
}
