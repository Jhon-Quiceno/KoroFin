package com.korofin.backend.ai.service;

import com.korofin.backend.ai.dto.AiUsageResponse;
import com.korofin.backend.ai.dto.ChatMessageResponse;
import com.korofin.backend.ai.dto.ChatReplyResponse;
import com.korofin.backend.ai.dto.ChatRequest;
import com.korofin.backend.ai.entity.AiMessage;
import com.korofin.backend.ai.entity.AiMessageKind;
import com.korofin.backend.ai.entity.AiMessageRole;
import com.korofin.backend.ai.entity.AiUsageEventType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.ai.exception.AiMessageQuotaExceededException;
import com.korofin.backend.ai.mapper.AiMessageMapper;
import com.korofin.backend.ai.repository.AiMessageRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.SecurityUtils;
import com.korofin.backend.ai.service.provider.AiCallContext;
import com.korofin.backend.ai.service.provider.AiChatOrchestrator;
import com.korofin.backend.ai.service.provider.AiProviderProperties;
import com.korofin.backend.ai.service.provider.ChatCompletionResult;
import com.korofin.backend.ai.service.provider.ChatMessage;
import com.korofin.backend.ai.service.provider.FinancialContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lógica de negocio de {@code POST /api/ai/chat} y {@code GET /api/ai/chat/history}.
 *
 * <p>Toda llamada resuelve al usuario actual vía {@link SecurityUtils#getCurrentUserId()},
 * delega la resolución de proveedor y el failover transparente en
 * {@link AiChatOrchestrator#complete(List, AiCallContext)}, e inyecta el contexto financiero real
 * del usuario (ver {@link FinancialContextBuilder}) como prompt de sistema en cada llamada — el
 * asistente se "entrena" por request en vez de con fine-tuning.
 *
 * <p>Tanto la pregunta del usuario como la respuesta del asistente se persisten como filas de
 * {@link AiMessage} solo después de una llamada exitosa a {@link AiChatOrchestrator#complete} — si
 * todos los proveedores configurados fallan, se propaga una excepción terminal y no se escribe
 * nada, manteniendo el historial de conversación libre de turnos a medio completar.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /**
     * Cuántos de los turnos {@link AiMessageKind#CHAT} más recientes se reenvían al proveedor
     * para continuidad de conversación, además del nuevo mensaje del usuario.
     */
    private static final int HISTORY_WINDOW_SIZE = 10;

    /**
     * Formatea la fecha de reinicio de cuota en el texto exacto en español mostrado al usuario
     * final, p. ej. {@code "1 de agosto de 2026"}.
     */
    private static final DateTimeFormatter SPANISH_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));

    private final AiMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AiChatOrchestrator aiChatOrchestrator;
    private final FinancialContextBuilder contextBuilder;
    private final AiMessageMapper aiMessageMapper;
    private final AiProviderProperties aiProviderProperties;
    private final Clock clock;

    public AiChatService(
            AiMessageRepository messageRepository,
            UserRepository userRepository,
            AiChatOrchestrator aiChatOrchestrator,
            FinancialContextBuilder contextBuilder,
            AiMessageMapper aiMessageMapper,
            AiProviderProperties aiProviderProperties,
            Clock clock
    ) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.aiChatOrchestrator = aiChatOrchestrator;
        this.contextBuilder = contextBuilder;
        this.aiMessageMapper = aiMessageMapper;
        this.aiProviderProperties = aiProviderProperties;
        this.clock = clock;
    }

    /**
     * Reserva cuota, luego llama al proveedor de IA y persiste ambos turnos, todo dentro de la
     * misma transacción que {@link #reserveMonthlyQuota}.
     *
     * <p><b>Nota de diseño sobre corrección de cuota ante una falla de proveedor:</b> la reserva,
     * la llamada al proveedor y la persistencia de ambos turnos corren dentro de este único método
     * {@code @Transactional}, compartiendo una sola transacción de base de datos. Si
     * {@link AiChatOrchestrator#complete} (o la persistencia que le sigue) lanza, la excepción se
     * propaga fuera de este método, Spring revierte toda la transacción, y el {@code UPDATE}
     * emitido por {@link UserRepository#reserveAiChatQuota} se deshace junto con todo lo demás —
     * no hace falta ningún refund manual.
     */
    @Transactional
    public ChatReplyResponse chat(ChatRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        reserveMonthlyQuota(userId);

        List<ChatMessage> conversation = buildConversation(userId, request.message());
        ChatCompletionResult result = aiChatOrchestrator.complete(
                conversation, new AiCallContext(userId, AiUsageEventType.CHAT)
        );

        persistTurn(userId, AiMessageRole.USER, request.message(), null, null);
        AiMessage assistantMessage = persistTurn(
                userId, AiMessageRole.ASSISTANT, result.content(), result.providerName(), result.model()
        );

        return new ChatReplyResponse(
                result.content(), result.providerName(), result.model(), assistantMessage.getCreatedAt()
        );
    }

    /**
     * Reporta el uso de cuota de chat de IA del usuario actual para el mes calendario UTC en
     * curso (ver {@code app.ai.monthly-message-limit}), para {@code GET /api/ai/chat/usage}. Lee
     * el contador dedicado {@code users.ai_chat_used}/{@code ai_chat_period} (ver {@link User}) en
     * vez de contar filas de {@code ai_messages}, así que se mantiene exacto incluso tras la purga
     * de historial de chat en cada login (ver {@code UserService#login}).
     */
    @Transactional(readOnly = true)
    public AiUsageResponse getUsage() {
        Long userId = SecurityUtils.getCurrentUserId();
        int limit = aiProviderProperties.getMonthlyMessageLimit();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        int used = currentUtcPeriod().equals(user.getAiChatPeriod()) ? user.getAiChatUsed() : 0;
        int remaining = Math.max(0, limit - used);
        Instant resetsAt = firstDayOfNextUtcMonth().atStartOfDay(ZoneOffset.UTC).toInstant();

        return new AiUsageResponse(used, limit, remaining, resetsAt);
    }

    /**
     * Reserva atómicamente una unidad de la cuota mensual de chat de IA del usuario actual antes
     * de contactar a cualquier proveedor, bloqueando el request una vez que ya se reservó
     * {@code app.ai.monthly-message-limit} para el mes calendario UTC actual.
     */
    private void reserveMonthlyQuota(Long userId) {
        int limit = aiProviderProperties.getMonthlyMessageLimit();
        String period = currentUtcPeriod();
        int rowsUpdated = userRepository.reserveAiChatQuota(userId, period, limit);

        if (rowsUpdated == 0) {
            int used = userRepository.findById(userId).map(User::getAiChatUsed).orElse(limit);
            log.warn("ai_chat_quota_rejected userId={} used={} limit={}", userId, used, limit);

            String resetDate = SPANISH_DATE_FORMATTER.format(firstDayOfNextUtcMonth());
            throw new AiMessageQuotaExceededException(
                    "Alcanzaste el límite de %d mensajes de IA este mes. Tu límite se reinicia el %s."
                            .formatted(limit, resetDate)
            );
        }
    }

    /** Mes calendario UTC actual, formateado {@code "YYYY-MM"}, coincidiendo con {@code users.ai_chat_period}. */
    private String currentUtcPeriod() {
        return YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();
    }

    private LocalDate firstDayOfNextUtcMonth() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC))
                .withDayOfMonth(1)
                .plusMonths(1);
    }

    /**
     * Devuelve el historial de chat del usuario actual, más reciente primero, paginado.
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getHistory(Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return messageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(userId, AiMessageKind.CHAT, pageable)
                .map(aiMessageMapper::toChatResponse);
    }

    private List<ChatMessage> buildConversation(Long userId, String newUserMessage) {
        String systemPrompt = contextBuilder.buildSystemPrompt();
        List<AiMessage> recentHistoryDesc = messageRepository
                .findByUser_IdAndKindOrderByCreatedAtDesc(userId, AiMessageKind.CHAT, PageRequest.of(0, HISTORY_WINDOW_SIZE))
                .getContent();

        List<AiMessage> chronological = new ArrayList<>(recentHistoryDesc);
        Collections.reverse(chronological);

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(systemPrompt));
        for (AiMessage message : chronological) {
            conversation.add(message.getRole() == AiMessageRole.USER
                    ? ChatMessage.user(message.getContent())
                    : ChatMessage.assistant(message.getContent()));
        }
        conversation.add(ChatMessage.user(newUserMessage));
        return conversation;
    }

    private AiMessage persistTurn(Long userId, AiMessageRole role, String content, String providerName, String model) {
        AiMessage message = new AiMessage();
        message.setUser(userRepository.getReferenceById(userId));
        message.setRole(role);
        message.setKind(AiMessageKind.CHAT);
        message.setContent(content);
        message.setProviderName(providerName);
        message.setModel(model);
        return messageRepository.save(message);
    }
}
