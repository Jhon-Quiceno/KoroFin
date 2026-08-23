package com.korofin.backend.service.integration.telegram;

import com.korofin.backend.dto.ai.MovementClassification;
import com.korofin.backend.dto.ai.ReceiptExtraction;
import com.korofin.backend.dto.ai.SummaryPeriod;
import com.korofin.backend.dto.ai.SummaryQueryIntent;
import com.korofin.backend.dto.ai.SummaryTopic;
import com.korofin.backend.dto.expense.ExpenseRequest;
import com.korofin.backend.dto.income.IncomeRequest;
import com.korofin.backend.dto.integration.TelegramReplyResponse;
import com.korofin.backend.entity.expense.CategoryType;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.exception.integration.TelegramImplausibleMovementException;
import com.korofin.backend.exception.integration.TelegramRateLimitExceededException;
import com.korofin.backend.repository.debt.DebtRepository;
import com.korofin.backend.repository.expense.CategoryTotalProjection;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.income.IncomeCategoryTotalProjection;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.security.InMemoryRateLimiter;
import com.korofin.backend.service.ai.AiCategorizationService;
import com.korofin.backend.service.ai.FinancialSummaryQueryService;
import com.korofin.backend.service.ai.ReceiptExtractionService;
import com.korofin.backend.service.expense.ExpenseService;
import com.korofin.backend.service.income.IncomeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

/**
 * Flujo completo de registro/consulta de movimientos por chat de Telegram (ver
 * docs/backend-plan.md sección 5): {@link #registerFromMessage} atiende
 * {@code POST /api/integrations/telegram/expenses}, {@link #registerFromReceipt} atiende
 * {@code POST /api/integrations/telegram/receipts}.
 *
 * <p>Instancia su propio {@link InMemoryRateLimiter}, distinto e independiente del
 * {@code RateLimitFilter} global de la fase 1 (que no conoce las rutas de Telegram) — mismo
 * criterio de "bucket dedicado por dominio" documentado en el Javadoc de esa clase.
 */
@Service
public class TelegramExpenseService {

    private static final Logger log = LoggerFactory.getLogger(TelegramExpenseService.class);

    private final TelegramLinkService telegramLinkService;
    private final TelegramIntentDetector telegramIntentDetector;
    private final TelegramMessageParser telegramMessageParser;
    private final AiCategorizationService aiCategorizationService;
    private final FinancialSummaryQueryService financialSummaryQueryService;
    private final ReceiptExtractionService receiptExtractionService;
    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final DebtRepository debtRepository;
    private final Clock clock;

    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter();
    private final int rateLimitMaxRequests;
    private final Duration rateLimitWindow;

    public TelegramExpenseService(
            TelegramLinkService telegramLinkService,
            TelegramIntentDetector telegramIntentDetector,
            TelegramMessageParser telegramMessageParser,
            AiCategorizationService aiCategorizationService,
            FinancialSummaryQueryService financialSummaryQueryService,
            ReceiptExtractionService receiptExtractionService,
            ExpenseService expenseService,
            IncomeService incomeService,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            DebtRepository debtRepository,
            Clock clock,
            @Value("${app.telegram.rate-limit.max-requests:5}") int rateLimitMaxRequests,
            @Value("${app.telegram.rate-limit.window-seconds:60}") long rateLimitWindowSeconds
    ) {
        this.telegramLinkService = telegramLinkService;
        this.telegramIntentDetector = telegramIntentDetector;
        this.telegramMessageParser = telegramMessageParser;
        this.aiCategorizationService = aiCategorizationService;
        this.financialSummaryQueryService = financialSummaryQueryService;
        this.receiptExtractionService = receiptExtractionService;
        this.expenseService = expenseService;
        this.incomeService = incomeService;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.debtRepository = debtRepository;
        this.clock = clock;
        this.rateLimitMaxRequests = rateLimitMaxRequests;
        this.rateLimitWindow = Duration.ofSeconds(rateLimitWindowSeconds);
    }

    /**
     * (1) rate limit propio por {@code chatId}; (2) {@link TelegramIntentDetector} decide pregunta
     * vs. registro; (3) si es pregunta, delega en {@link FinancialSummaryQueryService}; (4) si es
     * registro, {@link TelegramMessageParser} extrae monto/descripción; (5) clasifica con
     * {@link AiCategorizationService} — si falla, degrada a {@code EXPENSE} sin categoría.
     */
    @Transactional
    public TelegramReplyResponse registerFromMessage(Long telegramChatId, String text) {
        consumeRateLimitOrThrow(telegramChatId);
        Long userId = telegramLinkService.resolveUserId(telegramChatId);

        if (telegramIntentDetector.looksLikeSummaryQuery(text)) {
            return answerSummaryQuery(userId, text);
        }
        return registerMovementFromText(userId, text);
    }

    /**
     * Igual flujo que {@link #registerFromMessage}, pero el movimiento se extrae con
     * {@link ReceiptExtractionService} a partir de una foto en vez de con
     * {@link TelegramMessageParser} a partir de texto. No hay confirmación intermedia (a
     * diferencia de {@code ReceiptScanController} de la app): Telegram registra el movimiento
     * directamente, ya que no hay pantalla donde el usuario pueda revisar los datos antes.
     *
     * @throws TelegramImplausibleMovementException si la imagen no fue reconocida como un recibo real
     */
    @Transactional
    public TelegramReplyResponse registerFromReceipt(Long telegramChatId, String imageDataUri) {
        consumeRateLimitOrThrow(telegramChatId);
        Long userId = telegramLinkService.resolveUserId(telegramChatId);

        ReceiptExtraction extraction = receiptExtractionService.extractFromImage(userId, imageDataUri);
        if (!extraction.isReceipt()) {
            throw new TelegramImplausibleMovementException(
                    "No pude leer un recibo válido en esa imagen. Probá con una foto más clara, o registrá el "
                            + "gasto escribiéndolo como texto."
            );
        }

        return persistMovement(
                userId, extraction.movementType(), extraction.amount(), extraction.description(),
                extraction.categoryId()
        );
    }

    private void consumeRateLimitOrThrow(Long telegramChatId) {
        String key = "telegram-chat:" + telegramChatId;
        if (!rateLimiter.tryConsume(key, rateLimitMaxRequests, rateLimitWindow)) {
            throw new TelegramRateLimitExceededException(
                    "Estás enviando mensajes muy rápido. Esperá un momento antes de intentar de nuevo."
            );
        }
    }

    private TelegramReplyResponse registerMovementFromText(Long userId, String text) {
        TelegramMessageParser.ParsedMovement parsed = telegramMessageParser.parse(text);

        MovementClassification classification;
        try {
            classification = aiCategorizationService.classifyMovement(userId, parsed.description(), parsed.amount());
        } catch (RuntimeException ex) {
            log.warn("telegram_classify_movement_failed userId={}", userId, ex);
            classification = new MovementClassification(CategoryType.EXPENSE, null, null);
        }

        return persistMovement(
                userId, classification.type(), parsed.amount(), parsed.description(), classification.categoryId()
        );
    }

    private TelegramReplyResponse persistMovement(
            Long userId, CategoryType type, BigDecimal amount, String description, Long categoryId
    ) {
        LocalDate today = LocalDate.now(clock);
        String verb;

        if (type == CategoryType.INCOME) {
            incomeService.createIncome(userId, new IncomeRequest(amount, description, today, categoryId));
            verb = "Registré un ingreso";
        } else {
            expenseService.createExpense(
                    userId, new ExpenseRequest(amount, description, today, PaymentMethodType.OTHER, categoryId)
            );
            verb = "Registré un gasto";
        }

        String message = verb + " de $" + formatAmount(amount) + " (\"" + description + "\").";
        return new TelegramReplyResponse(message);
    }

    private TelegramReplyResponse answerSummaryQuery(Long userId, String text) {
        SummaryQueryIntent intent = financialSummaryQueryService.parseQuery(userId, text);

        if (intent.topic() == SummaryTopic.DEBT) {
            return new TelegramReplyResponse(buildDebtAnswer(userId));
        }
        return new TelegramReplyResponse(buildMovementAnswer(userId, intent));
    }

    private String buildDebtAnswer(Long userId) {
        BigDecimal totalDebt = nullToZero(debtRepository.sumRemainingAmountByUser(userId));
        if (totalDebt.signum() <= 0) {
            return "No tenés deudas activas registradas. ¡Vas muy bien!";
        }
        return "Debés un total de $" + formatAmount(totalDebt) + " entre todas tus deudas activas.";
    }

    private String buildMovementAnswer(Long userId, SummaryQueryIntent intent) {
        LocalDate[] range = resolveRange(intent.period());
        LocalDate start = range[0];
        LocalDate end = range[1];
        String periodLabel = periodLabel(intent.period());

        if (intent.categoryName() != null) {
            return buildCategoryAnswer(userId, start, end, intent, periodLabel);
        }

        if (intent.movementType() == CategoryType.INCOME) {
            BigDecimal income = nullToZero(incomeRepository.sumAmountByUserAndPeriod(userId, start, end));
            return "Llevás $" + formatAmount(income) + " en ingresos " + periodLabel + ".";
        }

        if (intent.movementType() == CategoryType.EXPENSE) {
            BigDecimal expense = nullToZero(expenseRepository.sumAmountByUserAndPeriod(userId, start, end));
            return "Llevás $" + formatAmount(expense) + " en gastos " + periodLabel + ".";
        }

        BigDecimal income = nullToZero(incomeRepository.sumAmountByUserAndPeriod(userId, start, end));
        BigDecimal expense = nullToZero(expenseRepository.sumAmountByUserAndPeriod(userId, start, end));
        BigDecimal balance = income.subtract(expense);
        return "Ingresos " + periodLabel + ": $" + formatAmount(income) + ". Gastos: $" + formatAmount(expense)
                + ". Balance: $" + formatAmount(balance) + ".";
    }

    private String buildCategoryAnswer(
            Long userId, LocalDate start, LocalDate end, SummaryQueryIntent intent, String periodLabel
    ) {
        boolean isIncome = intent.movementType() == CategoryType.INCOME;
        BigDecimal categoryTotal = isIncome
                ? findIncomeCategoryTotal(userId, start, end, intent.categoryName())
                : findExpenseCategoryTotal(userId, start, end, intent.categoryName());
        String typeLabel = isIncome ? "ingresos" : "gastos";

        return "En \"" + intent.categoryName() + "\" llevás $" + formatAmount(categoryTotal) + " en " + typeLabel
                + " " + periodLabel + ".";
    }

    private BigDecimal findExpenseCategoryTotal(Long userId, LocalDate start, LocalDate end, String categoryName) {
        List<CategoryTotalProjection> totals = expenseRepository.findTopCategoriesByUserAndPeriod(userId, start, end);
        return totals.stream()
                .filter(projection -> matchesCategory(projection.getCategoryName(), categoryName))
                .map(CategoryTotalProjection::getTotal)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal findIncomeCategoryTotal(Long userId, LocalDate start, LocalDate end, String categoryName) {
        List<IncomeCategoryTotalProjection> totals = incomeRepository.findTopCategoriesByUserAndPeriod(userId, start, end);
        return totals.stream()
                .filter(projection -> matchesCategory(projection.getCategoryName(), categoryName))
                .map(IncomeCategoryTotalProjection::getTotal)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static boolean matchesCategory(String actualName, String queriedName) {
        return actualName != null && actualName.toLowerCase(Locale.ROOT).contains(queriedName.toLowerCase(Locale.ROOT));
    }

    private LocalDate[] resolveRange(SummaryPeriod period) {
        LocalDate today = LocalDate.now(clock);
        return switch (period) {
            case TODAY -> new LocalDate[]{today, today};
            case WEEK -> new LocalDate[]{today.minusDays(6), today};
            case MONTH -> new LocalDate[]{YearMonth.from(today).atDay(1), today};
            case LAST_MONTH -> {
                YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
                yield new LocalDate[]{lastMonth.atDay(1), lastMonth.atEndOfMonth()};
            }
            case YEAR -> new LocalDate[]{today.withDayOfYear(1), today};
        };
    }

    private static String periodLabel(SummaryPeriod period) {
        return switch (period) {
            case TODAY -> "hoy";
            case WEEK -> "esta semana";
            case MONTH -> "este mes";
            case LAST_MONTH -> "el mes pasado";
            case YEAR -> "este año";
        };
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
