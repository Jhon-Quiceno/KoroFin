package com.korofin.backend.ai.service;

import com.korofin.backend.ai.dto.InsightResponse;
import com.korofin.backend.ai.entity.AiMessage;
import com.korofin.backend.ai.entity.AiMessageKind;
import com.korofin.backend.ai.entity.AiMessageRole;
import com.korofin.backend.ai.entity.AiUsageEventType;
import com.korofin.backend.ai.mapper.AiMessageMapper;
import com.korofin.backend.ai.repository.AiMessageRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.SecurityUtils;
import com.korofin.backend.ai.service.provider.AiCallContext;
import com.korofin.backend.ai.service.provider.AiChatOrchestrator;
import com.korofin.backend.ai.service.provider.ChatCompletionResult;
import com.korofin.backend.ai.service.provider.ChatMessage;
import com.korofin.backend.ai.service.provider.FinancialContextBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lógica de negocio de {@code GET /api/ai/insights} y {@code POST /api/ai/insights/generate}.
 *
 * <p>Los insights se persisten como filas {@link AiMessageKind#INSIGHT} para que el dashboard
 * pueda mostrar el más reciente sin llamar al proveedor de IA en cada carga de pantalla,
 * manteniéndose bien por debajo de los límites de los tiers gratuitos que este proyecto apunta —
 * el usuario regenera uno nuevo bajo demanda vía {@link #generateInsight()}.
 */
@Service
public class AiInsightService {

    private static final String INSIGHT_INSTRUCTION =
            "Con base en el contexto financiero anterior, genera entre 3 y 5 recomendaciones financieras "
                    + "personalizadas, concretas y accionables, en español, en formato de lista con viñetas "
                    + "('- '). No repitas los datos ya provistos: concéntrate en recomendaciones.";

    private final AiMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AiChatOrchestrator aiChatOrchestrator;
    private final FinancialContextBuilder contextBuilder;
    private final AiMessageMapper aiMessageMapper;

    public AiInsightService(
            AiMessageRepository messageRepository,
            UserRepository userRepository,
            AiChatOrchestrator aiChatOrchestrator,
            FinancialContextBuilder contextBuilder,
            AiMessageMapper aiMessageMapper
    ) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.aiChatOrchestrator = aiChatOrchestrator;
        this.contextBuilder = contextBuilder;
        this.aiMessageMapper = aiMessageMapper;
    }

    /**
     * @return el insight más recientemente generado del usuario actual, o {@code null} si nunca
     *         se generó ninguno (el controller lo mapea a {@code 204 No Content})
     */
    @Transactional(readOnly = true)
    public InsightResponse getLatestInsight() {
        Long userId = SecurityUtils.getCurrentUserId();
        return messageRepository.findFirstByUser_IdAndKindOrderByCreatedAtDesc(userId, AiMessageKind.INSIGHT)
                .map(aiMessageMapper::toInsightResponse)
                .orElse(null);
    }

    @Transactional
    public InsightResponse generateInsight() {
        Long userId = SecurityUtils.getCurrentUserId();
        String systemPrompt = contextBuilder.buildSystemPrompt();

        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(INSIGHT_INSTRUCTION)
        );
        ChatCompletionResult result = aiChatOrchestrator.complete(
                messages, new AiCallContext(userId, AiUsageEventType.INSIGHT)
        );

        AiMessage insight = new AiMessage();
        insight.setUser(userRepository.getReferenceById(userId));
        insight.setRole(AiMessageRole.ASSISTANT);
        insight.setKind(AiMessageKind.INSIGHT);
        insight.setContent(result.content());
        insight.setProviderName(result.providerName());
        insight.setModel(result.model());

        return aiMessageMapper.toInsightResponse(messageRepository.save(insight));
    }
}
