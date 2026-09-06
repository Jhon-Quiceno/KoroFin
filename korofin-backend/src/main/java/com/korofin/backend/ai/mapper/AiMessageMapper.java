package com.korofin.backend.ai.mapper;

import com.korofin.backend.ai.dto.ChatMessageResponse;
import com.korofin.backend.ai.dto.InsightResponse;
import com.korofin.backend.ai.entity.AiMessage;
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
