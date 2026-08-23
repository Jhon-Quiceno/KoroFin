package com.korofin.backend.statement.service.ai;

import com.korofin.backend.statement.dto.MovementType;
import com.korofin.backend.statement.dto.ParsedTransaction;
import com.korofin.backend.ai.entity.AiUsageEventType;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.statement.exception.StatementExtractionException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementAiExtractionServiceTest {

    @Mock
    private AiChatOrchestrator aiChatOrchestrator;

    private StatementAiExtractionService service;

    @BeforeEach
    void setUp() {
        service = new StatementAiExtractionService(aiChatOrchestrator, JsonMapper.builder().build());
    }

    @Test
    void extractParsesCleanJsonArrayAndMatchesCategories() {
        Category comida = buildCategory(2L, "Comida", CategoryType.EXPENSE);
        Category salario = buildCategory(5L, "Salario", CategoryType.INCOME);
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"2026-06-05\",\"description\":\"Supermercado Exito\",\"amount\":187500,"
                        + "\"movementType\":\"EXPENSE\",\"suggestedCategoryName\":\"Comida\"},"
                        + "{\"date\":\"2026-06-06\",\"description\":\"Salario agosto\",\"amount\":4200000,"
                        + "\"movementType\":\"INCOME\",\"suggestedCategoryName\":\"Salario\"}]",
                "nvidia", "nvidia/model", 200, 50
        ));

        List<ParsedTransaction> result = service.extract(
                "texto del extracto", List.of(salario), List.of(comida), 1L
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(result.get(0).movementType()).isEqualTo(MovementType.EXPENSE);
        assertThat(result.get(0).suggestedCategoryId()).isEqualTo(2L);
        assertThat(result.get(1).movementType()).isEqualTo(MovementType.INCOME);
        assertThat(result.get(1).suggestedCategoryId()).isEqualTo(5L);

        ArgumentCaptor<AiCallContext> ctxCaptor = ArgumentCaptor.forClass(AiCallContext.class);
        verify(aiChatOrchestrator).complete(anyList(), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue()).isEqualTo(new AiCallContext(1L, AiUsageEventType.STATEMENT_EXTRACT));
    }

    @Test
    void extractParsesSlashFormattedDates() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"05/06/2026\",\"description\":\"Retiro cajero\",\"amount\":50000,"
                        + "\"movementType\":\"EXPENSE\",\"suggestedCategoryName\":null}]",
                "nvidia", "nvidia/model", null, null
        ));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(), 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    void extractParsesJsonWrappedInMarkdownCodeFence() {
        String fenced = """
                ```json
                [{"date":"2026-06-05","description":"Uber","amount":18900,"movementType":"EXPENSE","suggestedCategoryName":null}]
                ```
                """;
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult(fenced, "nvidia", "nvidia/model", null, null));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(), 3L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("Uber");
    }

    @Test
    void extractDropsRowsWithInvalidDate() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"no-es-fecha\",\"description\":\"X\",\"amount\":1000,"
                        + "\"movementType\":\"EXPENSE\",\"suggestedCategoryName\":null}]",
                "nvidia", "nvidia/model", null, null
        ));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(), 4L);

        assertThat(result).isEmpty();
    }

    @Test
    void extractDropsRowsWithZeroAmount() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"2026-06-05\",\"description\":\"X\",\"amount\":0,"
                        + "\"movementType\":\"EXPENSE\",\"suggestedCategoryName\":null}]",
                "nvidia", "nvidia/model", null, null
        ));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(), 5L);

        assertThat(result).isEmpty();
    }

    @Test
    void extractNormalizesNegativeAmountToAbsoluteValue() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"2026-06-05\",\"description\":\"Compra\",\"amount\":-50000,"
                        + "\"movementType\":\"EXPENSE\",\"suggestedCategoryName\":null}]",
                "nvidia", "nvidia/model", null, null
        ));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(), 6L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    void extractDropsRowsWithUnrecognizedMovementType() {
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"2026-06-05\",\"description\":\"X\",\"amount\":1000,"
                        + "\"movementType\":\"NOSEQUE\",\"suggestedCategoryName\":null}]",
                "nvidia", "nvidia/model", null, null
        ));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(), 7L);

        assertThat(result).isEmpty();
    }

    @Test
    void extractReturnsNullSuggestedCategoryWhenNameDoesNotMatchAnyCategory() {
        Category comida = buildCategory(1L, "Comida", CategoryType.EXPENSE);
        when(aiChatOrchestrator.complete(anyList(), any())).thenReturn(new ChatCompletionResult(
                "[{\"date\":\"2026-06-05\",\"description\":\"X\",\"amount\":1000,"
                        + "\"movementType\":\"EXPENSE\",\"suggestedCategoryName\":\"Inexistente\"}]",
                "nvidia", "nvidia/model", null, null
        ));

        List<ParsedTransaction> result = service.extract("texto", List.of(), List.of(comida), 8L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).suggestedCategoryId()).isNull();
        assertThat(result.get(0).suggestedCategoryName()).isNull();
    }

    @Test
    void extractThrowsStatementExtractionExceptionWhenResponseIsNotJson() {
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult("esto no es JSON", "nvidia", "nvidia/model", null, null));

        assertThatThrownBy(() -> service.extract("texto", List.of(), List.of(), 9L))
                .isInstanceOf(StatementExtractionException.class);
    }

    @Test
    void extractThrowsStatementExtractionExceptionWhenResponseIsNull() {
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult(null, "nvidia", "nvidia/model", null, null));

        assertThatThrownBy(() -> service.extract("texto", List.of(), List.of(), 10L))
                .isInstanceOf(StatementExtractionException.class);
    }

    @Test
    void extractTruncatesVeryLongStatementTextBeforeSendingToProvider() {
        String longText = "x".repeat(30_000);
        when(aiChatOrchestrator.complete(anyList(), any()))
                .thenReturn(new ChatCompletionResult("[]", "nvidia", "nvidia/model", null, null));

        service.extract(longText, List.of(), List.of(), 11L);

        ArgumentCaptor<List<com.korofin.backend.ai.service.provider.ChatMessage>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(aiChatOrchestrator).complete(messagesCaptor.capture(), any());
        String sentUserContent = messagesCaptor.getValue().get(1).content();
        assertThat(sentUserContent.length()).isEqualTo(20_000);
    }

    private static Category buildCategory(Long id, String name, CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        return category;
    }
}
