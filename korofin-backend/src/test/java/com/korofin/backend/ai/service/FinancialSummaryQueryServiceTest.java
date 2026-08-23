package com.korofin.backend.ai.service;

import com.korofin.backend.ai.dto.SummaryPeriod;
import com.korofin.backend.ai.dto.SummaryQueryIntent;
import com.korofin.backend.ai.dto.SummaryTopic;
import com.korofin.backend.ai.entity.AiUsageEventType;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.ai.exception.AiProviderNotConfiguredException;
import com.korofin.backend.ai.service.provider.AiCallContext;
import com.korofin.backend.ai.service.provider.AiChatOrchestrator;
import com.korofin.backend.ai.service.provider.ChatCompletionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialSummaryQueryServiceTest {

    @Mock
    private AiChatOrchestrator aiChatOrchestrator;

    private FinancialSummaryQueryService service;

    @BeforeEach
    void setUp() {
        service = new FinancialSummaryQueryService(aiChatOrchestrator, JsonMapper.builder().build());
    }

    @Test
    void parseQueryReturnsTheDecidedIntentForCleanJsonWithAllFields() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"WEEK\",\"movementType\":\"EXPENSE\",\"categoryName\":\"Comida\"}", "groq", "llama-3.3", 10, 5));

        SummaryQueryIntent intent = service.parseQuery(1L, "¿Cuánto gasté en comida esta semana?");

        assertThat(intent.period()).isEqualTo(SummaryPeriod.WEEK);
        assertThat(intent.movementType()).isEqualTo(CategoryType.EXPENSE);
        assertThat(intent.categoryName()).isEqualTo("Comida");
        ArgumentCaptor<AiCallContext> ctxCaptor = ArgumentCaptor.forClass(AiCallContext.class);
        verify(aiChatOrchestrator).complete(anyList(), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue()).isEqualTo(new AiCallContext(1L, AiUsageEventType.CATEGORIZE));
    }

    @Test
    void parseQueryReturnsNullMovementTypeAndCategoryForABalanceQuestion() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"MONTH\",\"movementType\":null,\"categoryName\":null}", "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(2L, "resumen");

        assertThat(intent.period()).isEqualTo(SummaryPeriod.MONTH);
        assertThat(intent.movementType()).isNull();
        assertThat(intent.categoryName()).isNull();
    }

    @Test
    void parseQueryDefaultsToMonthWithNoFilterWhenTheModelResponseIsNotJson() {
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult("esto no es JSON", "groq", "llama-3.3", 3, 2));

        SummaryQueryIntent intent = service.parseQuery(3L, "algo raro");

        assertThat(intent).isEqualTo(new SummaryQueryIntent(SummaryPeriod.MONTH, null, null, SummaryTopic.MOVEMENT));
    }

    @Test
    void parseQueryDefaultsToMonthWithNoFilterWhenThePeriodValueIsUnrecognized() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"NOSEQUE\",\"movementType\":\"EXPENSE\",\"categoryName\":null}", "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(4L, "cuanto gaste");

        assertThat(intent.period()).isEqualTo(SummaryPeriod.MONTH);
        assertThat(intent.movementType()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    void parseQueryParsesJsonWrappedInAMarkdownCodeFence() {
        String fenced = """
                ```json
                {"period":"TODAY","movementType":"INCOME","categoryName":null}
                ```
                """;
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(fenced, "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(5L, "cuanto ingrese hoy");

        assertThat(intent.period()).isEqualTo(SummaryPeriod.TODAY);
        assertThat(intent.movementType()).isEqualTo(CategoryType.INCOME);
    }

    @Test
    void parseQueryDegradesToTheDefaultIntentWithoutThrowingWhenTheAiProviderFails() {
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenThrow(new AiProviderNotConfiguredException(AiChatOrchestrator.GENERIC_MESSAGE));

        SummaryQueryIntent intent = service.parseQuery(6L, "cuanto gaste");

        assertThat(intent).isEqualTo(new SummaryQueryIntent(SummaryPeriod.MONTH, null, null, SummaryTopic.MOVEMENT));
    }

    @Test
    void parseQueryTrimsTheCategoryNameFromTheModelResponse() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"MONTH\",\"movementType\":\"EXPENSE\",\"categoryName\":\"  Transporte  \"}",
                "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(7L, "cuanto gaste en transporte");

        assertThat(intent.categoryName()).isEqualTo("Transporte");
    }

    @Test
    void parseQueryReturnsTheDebtTopicWhenTheModelDetectsADebtQuestion() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"MONTH\",\"topic\":\"DEBT\",\"movementType\":null,\"categoryName\":null}",
                "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(8L, "¿cómo voy con mis deudas?");

        assertThat(intent.topic()).isEqualTo(SummaryTopic.DEBT);
    }

    @Test
    void parseQueryDefaultsToTheMovementTopicWhenTheTopicFieldIsMissing() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"MONTH\",\"movementType\":null,\"categoryName\":null}", "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(9L, "resumen");

        assertThat(intent.topic()).isEqualTo(SummaryTopic.MOVEMENT);
    }

    @Test
    void parseQueryDefaultsToTheMovementTopicWhenTheTopicValueIsUnrecognized() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"MONTH\",\"topic\":\"NOSEQUE\",\"movementType\":null,\"categoryName\":null}",
                "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(10L, "resumen");

        assertThat(intent.topic()).isEqualTo(SummaryTopic.MOVEMENT);
    }

    @Test
    void parseQueryRecognizesTheLastMonthPeriod() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"LAST_MONTH\",\"movementType\":\"EXPENSE\",\"categoryName\":null}",
                "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(11L, "cuanto gaste el mes pasado");

        assertThat(intent.period()).isEqualTo(SummaryPeriod.LAST_MONTH);
    }

    @Test
    void parseQueryRecognizesTheYearPeriod() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "{\"period\":\"YEAR\",\"movementType\":\"EXPENSE\",\"categoryName\":null}",
                "groq", "llama-3.3", null, null));

        SummaryQueryIntent intent = service.parseQuery(12L, "cuanto gaste este año");

        assertThat(intent.period()).isEqualTo(SummaryPeriod.YEAR);
    }
}
