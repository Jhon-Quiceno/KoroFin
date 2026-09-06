package com.korofin.backend.integration.service.telegram;

import com.korofin.backend.ai.dto.MovementClassification;
import com.korofin.backend.ai.dto.ReceiptExtraction;
import com.korofin.backend.ai.dto.SummaryPeriod;
import com.korofin.backend.ai.dto.SummaryQueryIntent;
import com.korofin.backend.ai.dto.SummaryTopic;
import com.korofin.backend.expense.dto.ExpenseRequest;
import com.korofin.backend.expense.dto.ExpenseResponse;
import com.korofin.backend.income.dto.IncomeRequest;
import com.korofin.backend.income.dto.IncomeResponse;
import com.korofin.backend.integration.dto.TelegramReplyResponse;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.integration.exception.TelegramImplausibleMovementException;
import com.korofin.backend.integration.exception.TelegramRateLimitExceededException;
import com.korofin.backend.debt.repository.DebtRepository;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.ai.service.AiCategorizationService;
import com.korofin.backend.ai.service.FinancialSummaryQueryService;
import com.korofin.backend.ai.service.ReceiptExtractionService;
import com.korofin.backend.expense.service.ExpenseService;
import com.korofin.backend.income.service.IncomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramExpenseServiceTest {

    private static final Long CHAT_ID = 555L;
    private static final Long USER_ID = 1L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TelegramLinkService telegramLinkService;

    @Mock
    private AiCategorizationService aiCategorizationService;

    @Mock
    private FinancialSummaryQueryService financialSummaryQueryService;

    @Mock
    private ReceiptExtractionService receiptExtractionService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private IncomeService incomeService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private DebtRepository debtRepository;

    private TelegramExpenseService telegramExpenseService;

    @BeforeEach
    void setUp() {
        telegramExpenseService = new TelegramExpenseService(
                telegramLinkService, new TelegramIntentDetector(), new TelegramMessageParser(),
                aiCategorizationService, financialSummaryQueryService, receiptExtractionService,
                expenseService, incomeService, expenseRepository, incomeRepository, debtRepository,
                FIXED_CLOCK, 2, 60
        );
    }

    @Test
    void registerFromMessageParsesAndCreatesAnExpenseWhenClassifiedAsExpense() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(aiCategorizationService.classifyMovement(eq(USER_ID), eq("Uber"), any()))
                .thenReturn(new MovementClassification(CategoryType.EXPENSE, 7L, "Transporte"));
        when(expenseService.createExpense(eq(USER_ID), any(ExpenseRequest.class)))
                .thenReturn(new ExpenseResponse(1L, BigDecimal.valueOf(15000), "Uber", null, PaymentMethodType.OTHER, 7L, "Transporte"));

        TelegramReplyResponse reply = telegramExpenseService.registerFromMessage(CHAT_ID, "Uber 15000");

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).createExpense(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("15000");
        assertThat(captor.getValue().categoryId()).isEqualTo(7L);
        assertThat(captor.getValue().date()).isEqualTo(java.time.LocalDate.of(2026, 7, 15));
        assertThat(reply.message()).contains("gasto");
        verify(incomeService, never()).createIncome(any(), any());
    }

    @Test
    void registerFromMessageCreatesAnIncomeWhenClassifiedAsIncome() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(aiCategorizationService.classifyMovement(eq(USER_ID), eq("Pago freelance"), any()))
                .thenReturn(new MovementClassification(CategoryType.INCOME, 3L, "Freelance"));
        when(incomeService.createIncome(eq(USER_ID), any(IncomeRequest.class)))
                .thenReturn(new IncomeResponse(2L, BigDecimal.valueOf(500000), "Pago freelance", null, 3L, "Freelance"));

        TelegramReplyResponse reply = telegramExpenseService.registerFromMessage(CHAT_ID, "Pago freelance 500000");

        verify(incomeService).createIncome(eq(USER_ID), any(IncomeRequest.class));
        assertThat(reply.message()).contains("ingreso");
    }

    @Test
    void registerFromMessageDegradesToExpenseWithoutCategoryWhenAiClassificationFails() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(aiCategorizationService.classifyMovement(eq(USER_ID), any(), any()))
                .thenThrow(new RuntimeException("provider down"));
        when(expenseService.createExpense(eq(USER_ID), any(ExpenseRequest.class)))
                .thenReturn(new ExpenseResponse(1L, BigDecimal.valueOf(15000), "Uber", null, PaymentMethodType.OTHER, null, null));

        telegramExpenseService.registerFromMessage(CHAT_ID, "Uber 15000");

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).createExpense(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().categoryId()).isNull();
    }

    @Test
    void registerFromMessagePropagatesImplausibleMovementFromTheParser() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);

        assertThatThrownBy(() -> telegramExpenseService.registerFromMessage(CHAT_ID, "hola sin monto"))
                .isInstanceOf(TelegramImplausibleMovementException.class);
        verify(expenseService, never()).createExpense(any(), any());
    }

    @Test
    void registerFromMessageThrowsRateLimitAfterExceedingTheConfiguredMax() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(aiCategorizationService.classifyMovement(any(), any(), any()))
                .thenReturn(new MovementClassification(CategoryType.EXPENSE, null, null));
        when(expenseService.createExpense(any(), any()))
                .thenReturn(new ExpenseResponse(1L, BigDecimal.TEN, "x", null, PaymentMethodType.OTHER, null, null));

        telegramExpenseService.registerFromMessage(CHAT_ID, "Uber 15000");
        telegramExpenseService.registerFromMessage(CHAT_ID, "Uber 15000");

        assertThatThrownBy(() -> telegramExpenseService.registerFromMessage(CHAT_ID, "Uber 15000"))
                .isInstanceOf(TelegramRateLimitExceededException.class);
    }

    @Test
    void registerFromMessageAnswersASummaryQueryInsteadOfRegisteringAMovement() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(financialSummaryQueryService.parseQuery(eq(USER_ID), any()))
                .thenReturn(new SummaryQueryIntent(SummaryPeriod.MONTH, null, null, SummaryTopic.MOVEMENT));
        when(incomeRepository.sumAmountByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(BigDecimal.valueOf(1000));
        when(expenseRepository.sumAmountByUserAndPeriod(eq(USER_ID), any(), any())).thenReturn(BigDecimal.valueOf(400));

        TelegramReplyResponse reply = telegramExpenseService.registerFromMessage(CHAT_ID, "¿cómo voy este mes?");

        assertThat(reply.message()).contains("Balance");
        verify(expenseService, never()).createExpense(any(), any());
        verify(incomeService, never()).createIncome(any(), any());
    }

    @Test
    void registerFromMessageAnswersADebtQueryUsingTotalRemainingDebt() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(financialSummaryQueryService.parseQuery(eq(USER_ID), any()))
                .thenReturn(new SummaryQueryIntent(SummaryPeriod.MONTH, null, null, SummaryTopic.DEBT));
        when(debtRepository.sumRemainingAmountByUser(USER_ID)).thenReturn(BigDecimal.valueOf(2_000_000));

        TelegramReplyResponse reply = telegramExpenseService.registerFromMessage(CHAT_ID, "cuanto debo?");

        assertThat(reply.message()).contains("2000000");
    }

    @Test
    void registerFromReceiptCreatesAnExpenseFromExtractedData() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(receiptExtractionService.extractFromImage(eq(USER_ID), any())).thenReturn(
                new ReceiptExtraction(true, "Éxito", BigDecimal.valueOf(45000), CategoryType.EXPENSE, 4L, "Comida")
        );
        when(expenseService.createExpense(eq(USER_ID), any(ExpenseRequest.class)))
                .thenReturn(new ExpenseResponse(1L, BigDecimal.valueOf(45000), "Éxito", null, PaymentMethodType.OTHER, 4L, "Comida"));

        TelegramReplyResponse reply = telegramExpenseService.registerFromReceipt(CHAT_ID, "data:image/jpeg;base64,AAAA");

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).createExpense(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("45000");
        assertThat(captor.getValue().categoryId()).isEqualTo(4L);
        assertThat(reply.message()).contains("gasto");
    }

    @Test
    void registerFromReceiptThrowsWhenTheImageIsNotRecognizedAsAReceipt() {
        when(telegramLinkService.resolveUserId(CHAT_ID)).thenReturn(USER_ID);
        when(receiptExtractionService.extractFromImage(eq(USER_ID), any())).thenReturn(ReceiptExtraction.notAReceipt());

        assertThatThrownBy(() -> telegramExpenseService.registerFromReceipt(CHAT_ID, "data:image/jpeg;base64,AAAA"))
                .isInstanceOf(TelegramImplausibleMovementException.class);
        verify(expenseService, never()).createExpense(any(), any());
    }
}
