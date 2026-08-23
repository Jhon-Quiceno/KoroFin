package com.korofin.backend.mapper.ai;

import com.korofin.backend.dto.ai.ChatMessageResponse;
import com.korofin.backend.dto.ai.InsightResponse;
import com.korofin.backend.entity.ai.AiMessage;
import com.korofin.backend.entity.ai.AiMessageKind;
import com.korofin.backend.entity.ai.AiMessageRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

class AiMessageMapperTest {

    private final AiMessageMapper mapper = Mappers.getMapper(AiMessageMapper.class);

    @Test
    void toChatResponseShouldMapAllFields() {
        AiMessage message = new AiMessage();
        message.setId(1L);
        message.setRole(AiMessageRole.ASSISTANT);
        message.setKind(AiMessageKind.CHAT);
        message.setContent("Vas bien este mes");
        message.setProviderName("groq");
        message.setModel("llama-3.3");
        message.setCreatedAt(Instant.parse("2026-07-03T10:00:00Z"));

        ChatMessageResponse response = mapper.toChatResponse(message);

        Assertions.assertEquals(1L, response.id());
        Assertions.assertEquals(AiMessageRole.ASSISTANT, response.role());
        Assertions.assertEquals("Vas bien este mes", response.content());
        Assertions.assertEquals("groq", response.providerName());
        Assertions.assertEquals("llama-3.3", response.model());
        Assertions.assertEquals(Instant.parse("2026-07-03T10:00:00Z"), response.createdAt());
    }

    @Test
    void toInsightResponseShouldMapAllFields() {
        AiMessage insight = new AiMessage();
        insight.setId(2L);
        insight.setKind(AiMessageKind.INSIGHT);
        insight.setContent("- Ahorra más");
        insight.setProviderName("nvidia");
        insight.setModel("meta/llama-3.1-70b-instruct");
        insight.setCreatedAt(Instant.parse("2026-07-05T08:00:00Z"));

        InsightResponse response = mapper.toInsightResponse(insight);

        Assertions.assertEquals(2L, response.id());
        Assertions.assertEquals("- Ahorra más", response.content());
        Assertions.assertEquals("nvidia", response.providerName());
        Assertions.assertEquals("meta/llama-3.1-70b-instruct", response.model());
        Assertions.assertEquals(Instant.parse("2026-07-05T08:00:00Z"), response.createdAt());
    }
}
