package com.korofin.backend.service.expense;

import com.korofin.backend.dto.expense.ExpenseRequest;
import com.korofin.backend.dto.expense.ExpenseResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.event.expense.ExpenseCreatedEvent;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.mapper.expense.ExpenseMapper;
import com.korofin.backend.repository.expense.CategoryRepository;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.expense.ExpenseSpecifications;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Lógica de negocio para administrar los gastos del usuario actual.
 *
 * <p>Toda operación resuelve al llamador vía {@link SecurityUtils#getCurrentUserId()} y limita
 * lecturas/escrituras estrictamente a ese usuario. Las mutaciones sobre un gasto ajeno, o un
 * {@code categoryId} que referencia una categoría ajena, lanzan {@link ResourceNotFoundException}
 * (404) en vez de 403, para no filtrar su existencia a quien no es su dueño.
 *
 * <p>{@link #createExpense} también publica {@link ExpenseCreatedEvent} después de guardar
 * exitosamente — todavía sin listeners en esta fase (ver Javadoc del evento). Solo la creación lo
 * publica; {@link #updateExpense} no, aunque cambien el monto o la fecha.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ExpenseMapper expenseMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            ExpenseMapper expenseMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.expenseMapper = expenseMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getExpenses(
            Long categoryId,
            LocalDate from,
            LocalDate to,
            PaymentMethodType paymentMethod,
            Pageable pageable
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Specification<Expense> spec = ExpenseSpecifications.ownedBy(userId)
                .and(ExpenseSpecifications.hasCategory(categoryId))
                .and(ExpenseSpecifications.dateFrom(from))
                .and(ExpenseSpecifications.dateTo(to))
                .and(ExpenseSpecifications.hasPaymentMethod(paymentMethod));

        return expenseRepository.findAll(spec, pageable)
                .map(expenseMapper::toResponse);
    }

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request) {
        return createExpense(SecurityUtils.getCurrentUserId(), request);
    }

    /**
     * Igual que {@link #createExpense(ExpenseRequest)} pero para un llamador que ya resolvió
     * {@code userId} por su cuenta en vez de leerlo de {@link SecurityUtils#getCurrentUserId()}.
     *
     * <p>Existe para el dominio {@code integration} (Telegram, fase 7): un webhook de n8n es
     * servidor-a-servidor, sin {@code SecurityContext} poblado por {@code JwtAuthenticationFilter}
     * — {@code TelegramExpenseService} resuelve el {@code userId} del chat vinculado por su cuenta
     * y necesita pasarlo explícito acá. Fase 2 omitió este overload a propósito (YAGNI, ningún
     * dominio lo necesitaba todavía) — ver docs/backend-plan.md sección 2.5.
     */
    @Transactional
    public ExpenseResponse createExpense(Long userId, ExpenseRequest request) {
        Expense expense = expenseMapper.toEntity(request);
        expense.setUser(userRepository.getReferenceById(userId));
        expense.setCategory(resolveOwnedCategory(request.categoryId(), userId));

        Expense savedExpense = expenseRepository.save(expense);
        eventPublisher.publishEvent(new ExpenseCreatedEvent(userId, savedExpense.getId()));
        return expenseMapper.toResponse(savedExpense);
    }

    @Transactional
    public ExpenseResponse updateExpense(Long expenseId, ExpenseRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Expense expense = findOwnedExpense(expenseId, userId);

        expenseMapper.updateEntityFromRequest(request, expense);
        expense.setCategory(resolveOwnedCategory(request.categoryId(), userId));

        Expense updatedExpense = expenseRepository.save(expense);
        return expenseMapper.toResponse(updatedExpense);
    }

    @Transactional
    public void deleteExpense(Long expenseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Expense expense = findOwnedExpense(expenseId, userId);
        expenseRepository.delete(expense);
    }

    private Expense findOwnedExpense(Long expenseId, Long userId) {
        return expenseRepository.findByIdAndUser_Id(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));
    }

    private Category resolveOwnedCategory(Long categoryId, Long userId) {
        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findByIdAndUser_Id(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
    }
}
