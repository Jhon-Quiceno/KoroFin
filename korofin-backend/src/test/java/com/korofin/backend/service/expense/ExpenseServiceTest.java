package com.korofin.backend.service.expense;

import com.korofin.backend.dto.expense.ExpenseRequest;
import com.korofin.backend.dto.expense.ExpenseResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.event.expense.ExpenseCreatedEvent;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.mapper.expense.ExpenseMapper;
import com.korofin.backend.repository.expense.CategoryRepository;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExpenseService expenseService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createExpenseShouldSaveExpenseForCurrentUserWithoutCategory() {
        setAuthenticatedUser(1L);
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(200), "Super", LocalDate.now(), PaymentMethodType.CASH, null
        );
        Expense mappedExpense = new Expense();
        Expense savedExpense = new Expense();
        savedExpense.setId(10L);
        ExpenseResponse response = new ExpenseResponse(
                10L, BigDecimal.valueOf(200), "Super", LocalDate.now(), PaymentMethodType.CASH, null, null
        );

        when(expenseMapper.toEntity(request)).thenReturn(mappedExpense);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(mappedExpense)).thenReturn(savedExpense);
        when(expenseMapper.toResponse(savedExpense)).thenReturn(response);

        ExpenseResponse createdExpense = expenseService.createExpense(request);

        Assertions.assertEquals(10L, createdExpense.id());
        Assertions.assertNull(mappedExpense.getCategory());
        Assertions.assertEquals(1L, mappedExpense.getUser().getId());
    }

    @Test
    void createExpenseShouldPublishExpenseCreatedEventWithOwnerAndSavedId() {
        setAuthenticatedUser(1L);
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(200), "Super", LocalDate.now(), PaymentMethodType.CASH, null
        );
        Expense mappedExpense = new Expense();
        Expense savedExpense = new Expense();
        savedExpense.setId(42L);

        when(expenseMapper.toEntity(request)).thenReturn(mappedExpense);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(mappedExpense)).thenReturn(savedExpense);
        when(expenseMapper.toResponse(savedExpense)).thenReturn(
                new ExpenseResponse(42L, BigDecimal.valueOf(200), "Super", LocalDate.now(), PaymentMethodType.CASH, null, null)
        );

        expenseService.createExpense(request);

        ArgumentCaptor<ExpenseCreatedEvent> captor = ArgumentCaptor.forClass(ExpenseCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        Assertions.assertEquals(1L, captor.getValue().userId());
        Assertions.assertEquals(42L, captor.getValue().expenseId());
    }

    @Test
    void updateExpenseShouldNotPublishExpenseCreatedEvent() {
        setAuthenticatedUser(1L);
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(100), "Renta", LocalDate.now(), PaymentMethodType.CASH, null
        );
        Expense existing = new Expense();
        existing.setId(20L);
        existing.setUser(buildUser(1L));
        when(expenseRepository.findByIdAndUser_Id(20L, 1L)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(existing)).thenReturn(existing);
        when(expenseMapper.toResponse(existing)).thenReturn(
                new ExpenseResponse(20L, BigDecimal.valueOf(100), "Renta", LocalDate.now(), PaymentMethodType.CASH, null, null)
        );

        expenseService.updateExpense(20L, request);

        verify(eventPublisher, never()).publishEvent(any(ExpenseCreatedEvent.class));
    }

    @Test
    void createExpenseShouldResolveOwnedCategoryWhenCategoryIdProvided() {
        setAuthenticatedUser(1L);
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(200), "Super", LocalDate.now(), PaymentMethodType.DEBIT_CARD, 4L
        );
        Expense mappedExpense = new Expense();
        Category category = new Category();
        category.setId(4L);
        category.setName("Alimentación");

        when(expenseMapper.toEntity(request)).thenReturn(mappedExpense);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(categoryRepository.findByIdAndUser_Id(4L, 1L)).thenReturn(Optional.of(category));
        when(expenseRepository.save(mappedExpense)).thenReturn(mappedExpense);
        when(expenseMapper.toResponse(mappedExpense)).thenReturn(
                new ExpenseResponse(1L, BigDecimal.valueOf(200), "Super", LocalDate.now(),
                        PaymentMethodType.DEBIT_CARD, 4L, "Alimentación")
        );

        ExpenseResponse createdExpense = expenseService.createExpense(request);

        Assertions.assertEquals(4L, createdExpense.categoryId());
        Assertions.assertEquals(category, mappedExpense.getCategory());
    }

    @Test
    void createExpenseShouldThrowNotFoundWhenCategoryBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(200), "Super", LocalDate.now(), PaymentMethodType.CASH, 99L
        );
        Expense mappedExpense = new Expense();

        when(expenseMapper.toEntity(request)).thenReturn(mappedExpense);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(categoryRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> expenseService.createExpense(request));
    }

    @Test
    void updateExpenseShouldThrowNotFoundWhenExpenseBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(100), null, LocalDate.now(), PaymentMethodType.CASH, null
        );
        when(expenseRepository.findByIdAndUser_Id(20L, 1L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> expenseService.updateExpense(20L, request));
    }

    @Test
    void deleteExpenseShouldDeleteWhenOwnedByCurrentUser() {
        setAuthenticatedUser(3L);
        Expense expense = new Expense();
        expense.setId(55L);
        expense.setUser(buildUser(3L));
        when(expenseRepository.findByIdAndUser_Id(55L, 3L)).thenReturn(Optional.of(expense));

        expenseService.deleteExpense(55L);

        verify(expenseRepository).delete(expense);
    }

    @Test
    void deleteExpenseShouldThrowNotFoundWhenOwnedByAnotherUser() {
        setAuthenticatedUser(3L);
        when(expenseRepository.findByIdAndUser_Id(55L, 3L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> expenseService.deleteExpense(55L));
    }

    @Test
    void getExpensesShouldApplyCategoryDateRangeAndPaymentMethodFilters() {
        setAuthenticatedUser(1L);
        Pageable pageable = PageRequest.of(0, 20);
        Expense expense = new Expense();
        Page<Expense> page = new PageImpl<>(List.of(expense), pageable, 1);
        ExpenseResponse response = new ExpenseResponse(
                1L, BigDecimal.TEN, null, LocalDate.now(), PaymentMethodType.CASH, 4L, "Alimentación"
        );
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(expenseMapper.toResponse(expense)).thenReturn(response);

        Page<ExpenseResponse> result = expenseService.getExpenses(4L, from, to, PaymentMethodType.CASH, pageable);

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(PaymentMethodType.CASH, result.getContent().get(0).paymentMethod());
    }

    @Test
    void getExpensesShouldAllowNullFilters() {
        setAuthenticatedUser(1L);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Expense> page = new PageImpl<>(List.of(), pageable, 0);

        when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ExpenseResponse> result = expenseService.getExpenses(null, null, null, null, pageable);

        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void createExpenseWithExplicitUserIdDoesNotReadSecurityContext() {
        ExpenseRequest request = new ExpenseRequest(
                BigDecimal.valueOf(50), "Uber", LocalDate.now(), PaymentMethodType.OTHER, null
        );
        Expense mappedExpense = new Expense();
        Expense savedExpense = new Expense();
        savedExpense.setId(77L);

        when(expenseMapper.toEntity(request)).thenReturn(mappedExpense);
        when(userRepository.getReferenceById(9L)).thenReturn(buildUser(9L));
        when(expenseRepository.save(mappedExpense)).thenReturn(savedExpense);
        when(expenseMapper.toResponse(savedExpense)).thenReturn(
                new ExpenseResponse(77L, BigDecimal.valueOf(50), "Uber", LocalDate.now(), PaymentMethodType.OTHER, null, null)
        );

        ExpenseResponse response = expenseService.createExpense(9L, request);

        Assertions.assertEquals(77L, response.id());
        Assertions.assertEquals(9L, mappedExpense.getUser().getId());
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }

    private User buildUser(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
