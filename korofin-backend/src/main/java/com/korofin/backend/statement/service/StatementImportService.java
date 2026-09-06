package com.korofin.backend.statement.service;

import com.korofin.backend.expense.dto.ExpenseRequest;
import com.korofin.backend.income.dto.IncomeRequest;
import com.korofin.backend.statement.dto.ImportConfirmRow;
import com.korofin.backend.statement.dto.ImportPreviewRow;
import com.korofin.backend.statement.dto.MovementType;
import com.korofin.backend.statement.dto.ParsedTransaction;
import com.korofin.backend.statement.dto.StatementConfirmRequest;
import com.korofin.backend.statement.dto.StatementImportResultResponse;
import com.korofin.backend.statement.dto.StatementPreviewResponse;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.income.entity.Income;
import com.korofin.backend.statement.exception.StatementExtractionException;
import com.korofin.backend.statement.exception.UnsupportedStatementFileException;
import com.korofin.backend.expense.repository.CategoryRepository;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.common.security.SecurityUtils;
import com.korofin.backend.expense.service.ExpenseService;
import com.korofin.backend.income.service.IncomeService;
import com.korofin.backend.statement.service.ai.StatementAiExtractionService;
import com.korofin.backend.statement.service.dedup.DuplicateDetector;
import com.korofin.backend.statement.service.extraction.StatementTextExtractionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta la importación de extractos bancarios: {@link #preview} extrae y muestra los
 * movimientos detectados sin persistir nada, y {@link #confirm} crea los ingresos/gastos que el
 * usuario decidió confirmar (posiblemente editados respecto de la previsualización).
 *
 * <p>No hay una entidad {@code StatementImport} persistida: el flujo es completamente stateless
 * entre {@link #preview} y {@link #confirm} — el cliente es responsable de reenviar en
 * {@link #confirm} las filas ya revisadas/editadas, {@link #preview} no guarda nada que
 * {@link #confirm} pueda referenciar por id.
 */
@Service
public class StatementImportService {

    /** Margen de días alrededor del rango de fechas extraído, usado para buscar posibles duplicados. */
    private static final int DUPLICATE_WINDOW_DAYS = 3;

    private final StatementTextExtractionService statementTextExtractionService;
    private final StatementAiExtractionService statementAiExtractionService;
    private final DuplicateDetector duplicateDetector;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;

    public StatementImportService(
            StatementTextExtractionService statementTextExtractionService,
            StatementAiExtractionService statementAiExtractionService,
            DuplicateDetector duplicateDetector,
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            ExpenseService expenseService,
            IncomeService incomeService
    ) {
        this.statementTextExtractionService = statementTextExtractionService;
        this.statementAiExtractionService = statementAiExtractionService;
        this.duplicateDetector = duplicateDetector;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
    }

    /**
     * Extrae y previsualiza los movimientos de un extracto bancario. No crea ningún
     * {@code Income}/{@code Expense}: el usuario debe revisar y confirmar explícitamente vía
     * {@link #confirm}.
     */
    @Transactional(readOnly = true)
    public StatementPreviewResponse preview(MultipartFile file, String password) {
        Long userId = SecurityUtils.getCurrentUserId();
        validateFile(file);

        String statementText = statementTextExtractionService.extractText(
                file.getOriginalFilename(), readBytes(file), password
        );

        List<Category> incomeCategories = categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(userId, CategoryType.INCOME);
        List<Category> expenseCategories = categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(userId, CategoryType.EXPENSE);

        List<ParsedTransaction> transactions = statementAiExtractionService.extract(
                statementText, incomeCategories, expenseCategories, userId
        );

        List<Boolean> duplicateFlags = detectDuplicates(userId, transactions);

        List<ImportPreviewRow> rows = new ArrayList<>(transactions.size());
        int duplicateCount = 0;
        for (int i = 0; i < transactions.size(); i++) {
            ParsedTransaction transaction = transactions.get(i);
            boolean isDuplicate = duplicateFlags.get(i);
            if (isDuplicate) {
                duplicateCount++;
            }
            rows.add(new ImportPreviewRow(
                    transaction.date(),
                    transaction.description(),
                    transaction.amount(),
                    transaction.movementType(),
                    isDuplicate,
                    transaction.suggestedCategoryId(),
                    transaction.suggestedCategoryName()
            ));
        }

        return new StatementPreviewResponse(rows, rows.size(), duplicateCount);
    }

    /**
     * Crea un ingreso o un gasto por cada fila confirmada por el usuario, reusando
     * {@link ExpenseService#createExpense} / {@link IncomeService#createIncome} en vez de duplicar
     * su lógica — la validación de propiedad de {@code categoryId} ocurre ahí, igual que en la
     * creación manual de movimientos.
     *
     * @return la cantidad de movimientos creados
     */
    @Transactional
    public StatementImportResultResponse confirm(StatementConfirmRequest request) {
        int createdCount = 0;
        for (ImportConfirmRow row : request.rows()) {
            if (row.movementType() == MovementType.INCOME) {
                incomeService.createIncome(new IncomeRequest(
                        row.amount(), row.description(), row.date(), row.categoryId()
                ));
            } else {
                PaymentMethodType paymentMethod = row.paymentMethod() != null ? row.paymentMethod() : PaymentMethodType.OTHER;
                expenseService.createExpense(new ExpenseRequest(
                        row.amount(), row.description(), row.date(), paymentMethod, row.categoryId()
                ));
            }
            createdCount++;
        }

        return new StatementImportResultResponse(createdCount);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedStatementFileException("Debe adjuntar un archivo de extracto.");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new StatementExtractionException("No se pudo leer el archivo subido.", ex);
        }
    }

    /**
     * Trae los ingresos/gastos existentes del usuario en una ventana de
     * {@link #DUPLICATE_WINDOW_DAYS} días alrededor del rango de fechas extraído, y delega en
     * {@link DuplicateDetector} la comparación fila por fila.
     */
    private List<Boolean> detectDuplicates(Long userId, List<ParsedTransaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        LocalDate minDate = transactions.stream().map(ParsedTransaction::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = transactions.stream().map(ParsedTransaction::date).max(LocalDate::compareTo).orElseThrow();
        LocalDate windowStart = minDate.minusDays(DUPLICATE_WINDOW_DAYS);
        LocalDate windowEnd = maxDate.plusDays(DUPLICATE_WINDOW_DAYS);

        List<Expense> existingExpenses = expenseRepository.findByUser_IdAndDateBetween(userId, windowStart, windowEnd);
        List<Income> existingIncomes = incomeRepository.findByUser_IdAndDateBetween(userId, windowStart, windowEnd);

        return duplicateDetector.detectDuplicates(transactions, existingExpenses, existingIncomes);
    }
}
