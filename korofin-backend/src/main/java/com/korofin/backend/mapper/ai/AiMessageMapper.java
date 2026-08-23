package com.korofin.backend.mapper.ai;

import com.korofin.backend.dto.ai.ChatMessageResponse;
import com.korofin.backend.dto.ai.InsightResponse;
import com.korofin.backend.entity.ai.AiMessage;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct entre {@link AiMessage} y sus DTOs de lectura.
 *
 * <p>{@link AiMessage} nunca se crea a partir de un DTO enviado por el cliente (los turnos de
 * chat y los insights siempre los arma {@code AiChatService}/{@code AiInsightService} del lado
 * del servidor), así que este mapper solo necesita las dos direcciones de lectura usadas por
 * {@code GET /api/ai/chat/history} y {@code GET /api/ai/insights} respectivamente.
 */
@Mapper(componentModel = "spring")
public interface AiMessageMapper {

    ChatMessageResponse toChatResponse(AiMessage message);

    InsightResponse toInsightResponse(AiMessage message);
}
