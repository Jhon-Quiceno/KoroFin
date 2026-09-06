package com.korofin.backend.statement.service;

import com.korofin.backend.expense.dto.ExpenseRequest;
import com.korofin.backend.expense.dto.ExpenseResponse;
import com.korofin.backend.income.dto.IncomeRequest;
import com.korofin.backend.income.dto.IncomeResponse;
import com.korofin.backend.statement.dto.ImportConfirmRow;
import com.korofin.backend.statement.dto.MovementType;
import com.korofin.backend.statement.dto.ParsedTransaction;
import com.korofin.backend.statement.dto.StatementConfirmRequest;
import com.korofin.backend.statement.dto.StatementImportResultResponse;
import com.korofin.backend.statement.dto.StatementPreviewResponse;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.statement.exception.UnsupportedStatementFileException;
import com.korofin.backend.expense.repository.CategoryRepository;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.expense.service.ExpenseService;
import com.korofin.backend.income.service.IncomeService;
import com.korofin.backend.statement.service.ai.StatementAiExtractionService;
import com.korofin.backend.statement.service.dedup.DuplicateDetector;
import com.korofin.backend.statement.service.extraction.StatementTextExtractionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock
    private StatementTextExtractionService statementTextExtractionService;

    @Mock
    private StatementAiExtractionService statementAiExtractionService;

    @Mock
    private DuplicateDetector duplicateDetector;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private IncomeService incomeService;

    @InjectMocks
    private StatementImportService statementImportService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewExtractsAndFlagsDuplicatesWithoutPersistingAnything() {
        setAuthenticatedUser(1L);
        MockMultipartFile file = new MockMultipartFile("file", "extracto.csv", "text/csv", "irrelevante".getBytes());
        when(categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(1L, CategoryType.INCOME)).thenReturn(List.of());
        when(categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(1L, CategoryType.EXPENSE)).thenReturn(List.of());
        when(statementTextExtractionService.extractText(eq("extracto.csv"), any(), eq((String) null)))
                .thenReturn("texto extraido");

        ParsedTransaction transaction1 = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Supermercado", BigDecimal.valueOf(187500), MovementType.EXPENSE, null, null
        );
        ParsedTransaction transaction2 = new ParsedTransaction(
                LocalDate.of(2026, 6, 6), "Salario", BigDecimal.valueOf(4200000), MovementType.INCOME, null, null
        );
        when(statementAiExtractionService.extract("texto extraido", List.of(), List.of(), 1L))
                .thenReturn(List.of(transaction1, transaction2));
        when(expenseRepository.findByUser_IdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of());
        when(incomeRepository.findByUser_IdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of());
        when(duplicateDetector.detectDuplicates(any(), any(), any())).thenReturn(List.of(true, false));

        StatementPreviewResponse response = statementImportService.preview(file, null);

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.duplicateRows()).isEqualTo(1);
        assertThat(response.rows().get(0).isDuplicate()).isTrue();
        assertThat(response.rows().get(1).isDuplicate()).isFalse();

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenseRepository).findByUser_IdAndDateBetween(eq(1L), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 6, 9));
    }

    @Test
    void previewThrowsUnsupportedStatementFileExceptionForEmptyFile() {
        setAuthenticatedUser(1L);
        MockMultipartFile emptyFile = new MockMultipartFile("file", "extracto.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> statementImportService.preview(emptyFile, null))
                .isInstanceOf(UnsupportedStatementFileException.class);
    }

    @Test
    void previewThrowsUnsupportedStatementFileExceptionForNullFile() {
        setAuthenticatedUser(1L);

        assertThatThrownBy(() -> statementImportService.preview(null, null))
                .isInstanceOf(UnsupportedStatementFileException.class);
    }

    @Test
    void previewReturnsEmptyResponseWhenNoTransactionsAreExtracted() {
        setAuthenticatedUser(1L);
        MockMultipartFile file = new MockMultipartFile("file", "extracto.csv", "text/csv", "irrelevante".getBytes());
        when(categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(1L, CategoryType.INCOME)).thenReturn(List.of());
        when(categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(1L, CategoryType.EXPENSE)).thenReturn(List.of());
        when(statementTextExtractionService.extractText(eq("extracto.csv"), any(), eq((String) null)))
                .thenReturn("texto extraido");
        when(statementAiExtractionService.extract(any(), any(), any(), eq(1L))).thenReturn(List.of());

        StatementPreviewResponse response = statementImportService.preview(file, null);

        assertThat(response.totalRows()).isZero();
        assertThat(response.duplicateRows()).isZero();
        verify(expenseRepository, times(0)).findByUser_IdAndDateBetween(any(), any(), any());
    }

    @Test
    void confirmCreatesExpenseForExpenseRowsUsingExpenseService() {
        ImportConfirmRow row = new ImportConfirmRow(
                MovementType.EXPENSE, BigDecimal.valueOf(85000), LocalDate.of(2026, 6, 5),
                "Supermercado", 2L, PaymentMethodType.DEBIT_CARD
        );
        when(expenseService.createExpense(any(ExpenseRequest.class))).thenReturn(
                new ExpenseResponse(1L, BigDecimal.valueOf(85000), "Supermercado", LocalDate.of(2026, 6, 5), PaymentMethodType.DEBIT_CARD, 2L, "Alimentacion")
        );

        StatementImportResultResponse result = statementImportService.confirm(new StatementConfirmRequest(List.of(row)));

        assertThat(result.createdCount()).isEqualTo(1);
        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).createExpense(captor.capture());
        assertThat(captor.getValue().paymentMethod()).isEqualTo(PaymentMethodType.DEBIT_CARD);
        assertThat(captor.getValue().amount()).isEqualByComparingTo(BigDecimal.valueOf(85000));
    }

    @Test
    void confirmDefaultsPaymentMethodToOtherWhenNotProvided() {
        ImportConfirmRow row = new ImportConfirmRow(
                MovementType.EXPENSE, BigDecimal.valueOf(1000), LocalDate.of(2026, 6, 5), "X", null, null
        );
        when(expenseService.createExpense(any(ExpenseRequest.class))).thenReturn(
                new ExpenseResponse(1L, BigDecimal.valueOf(1000), "X", LocalDate.of(2026, 6, 5), PaymentMethodType.OTHER, null, null)
        );

        statementImportService.confirm(new StatementConfirmRequest(List.of(row)));

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).createExpense(captor.capture());
        assertThat(captor.getValue().paymentMethod()).isEqualTo(PaymentMethodType.OTHER);
    }

    @Test
    void confirmCreatesIncomeForIncomeRowsUsingIncomeService() {
        ImportConfirmRow row = new ImportConfirmRow(
                MovementType.INCOME, BigDecimal.valueOf(4200000), LocalDate.of(2026, 6, 6), "Salario", 5L, null
        );
        when(incomeService.createIncome(any(IncomeRequest.class))).thenReturn(
                new IncomeResponse(2L, BigDecimal.valueOf(4200000), "Salario", LocalDate.of(2026, 6, 6), 5L, "Salario")
        );

        StatementImportResultResponse result = statementImportService.confirm(new StatementConfirmRequest(List.of(row)));

        assertThat(result.createdCount()).isEqualTo(1);
        ArgumentCaptor<IncomeRequest> captor = ArgumentCaptor.forClass(IncomeRequest.class);
        verify(incomeService).createIncome(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo(BigDecimal.valueOf(4200000));
    }

    @Test
    void confirmCreatesOneMovementPerRowInAMixedBatch() {
        ImportConfirmRow expenseRow = new ImportConfirmRow(
                MovementType.EXPENSE, BigDecimal.valueOf(1000), LocalDate.of(2026, 6, 5), "X", null, PaymentMethodType.CASH
        );
        ImportConfirmRow incomeRow = new ImportConfirmRow(
                MovementType.INCOME, BigDecimal.valueOf(2000), LocalDate.of(2026, 6, 6), "Y", null, null
        );
        when(expenseService.createExpense(any())).thenReturn(
                new ExpenseResponse(1L, BigDecimal.valueOf(1000), "X", LocalDate.of(2026, 6, 5), PaymentMethodType.CASH, null, null)
        );
        when(incomeService.createIncome(any())).thenReturn(
                new IncomeResponse(2L, BigDecimal.valueOf(2000), "Y", LocalDate.of(2026, 6, 6), null, null)
        );

        StatementImportResultResponse result = statementImportService.confirm(
                new StatementConfirmRequest(List.of(expenseRow, incomeRow))
        );

        assertThat(result.createdCount()).isEqualTo(2);
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
