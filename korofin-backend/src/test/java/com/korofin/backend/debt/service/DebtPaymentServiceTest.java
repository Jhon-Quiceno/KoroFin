package com.korofin.backend.debt.service;

import com.korofin.backend.debt.dto.DebtPaymentRequest;
import com.korofin.backend.debt.dto.DebtPaymentResponse;
import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.debt.entity.DebtPayment;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.debt.exception.DebtNotFoundException;
import com.korofin.backend.debt.exception.DebtPaymentExceedsBalanceException;
import com.korofin.backend.debt.mapper.DebtPaymentMapper;
import com.korofin.backend.debt.repository.DebtPaymentRepository;
import com.korofin.backend.debt.repository.DebtRepository;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtPaymentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DebtPaymentRepository debtPaymentRepository;

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DebtPaymentMapper debtPaymentMapper;

    private DebtPaymentService debtPaymentService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void buildService() {
        debtPaymentService = new DebtPaymentService(
                debtPaymentRepository, debtRepository, expenseRepository,
                userRepository, debtPaymentMapper, FIXED_CLOCK
        );
    }

    @Test
    void createPaymentDecrementsBalanceAtomicallyAndCreatesLinkedExpense() {
        buildService();
        setAuthenticatedUser(1L);
        Debt debt = buildDebt(3L, "Préstamo", "1000000", "1000000");
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("200000"), null, "Abono");

        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(debt));
        when(debtRepository.decrementRemainingAmount(3L, new BigDecimal("200000"))).thenReturn(1);
        when(debtRepository.findById(3L)).thenReturn(Optional.of(buildDebt(3L, "Préstamo", "1000000", "800000")));
        when(debtPaymentMapper.toEntity(request)).thenReturn(new DebtPayment());
        when(debtPaymentRepository.save(any(DebtPayment.class))).thenAnswer(invocation -> {
            DebtPayment saved = invocation.getArgument(0);
            saved.setId(40L);
            return saved;
        });
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(70L);
            return saved;
        });
        when(debtPaymentMapper.toResponse(any(DebtPayment.class))).thenReturn(
                new DebtPaymentResponse(40L, 3L, new BigDecimal("200000"), TODAY, "Abono", null, null)
        );

        DebtPaymentResponse response = debtPaymentService.createPayment(3L, request);

        // El saldo se reduce con el UPDATE atómico, nunca con un setter en Java.
        verify(debtRepository).decrementRemainingAmount(3L, new BigDecimal("200000"));

        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        Expense createdExpense = expenseCaptor.getValue();
        assertThat(createdExpense.getAmount()).isEqualByComparingTo("200000");
        assertThat(createdExpense.getDescription()).isEqualTo("Abono a deuda: Préstamo");
        assertThat(createdExpense.getPaymentMethod()).isEqualTo(PaymentMethodType.OTHER);
        assertThat(createdExpense.getCategory()).isNull();
        assertThat(createdExpense.getDebtPayment().getId()).isEqualTo(40L);
        assertThat(response.expenseId()).isEqualTo(70L);
    }

    @Test
    void createPaymentDefaultsPaymentDateToTodayFromTheInjectedClock() {
        buildService();
        setAuthenticatedUser(1L);
        Debt debt = buildDebt(3L, "Préstamo", "1000000", "1000000");
        DebtPaymentRequest request = new DebtPaymentRequest(new BigDecimal("1000"), null, null);

        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(debt));
        when(debtRepository.decrementRemainingAmount(eq(3L), any(BigDecimal.class))).thenReturn(1);
        when(debtRepository.findById(3L)).thenReturn(Optional.of(debt));
        when(debtPaymentMapper.toEntity(request)).thenReturn(new DebtPayment());
        when(debtPaymentRepository.save(any(DebtPayment.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));
        when(debtPaymentMapper.toResponse(any(DebtPayment.class))).thenReturn(
                new DebtPaymentResponse(1L, 3L, new BigDecimal("1000"), TODAY, null, null, null)
        );

        debtPaymentService.createPayment(3L, request);

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(debtPaymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentDate()).isEqualTo(TODAY);
    }

    @Test
    void createPaymentRejectsAmountAboveRemainingBalanceWithoutTouchingTheDatabase() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(3L, 1L))
                .thenReturn(Optional.of(buildDebt(3L, "Préstamo", "1000000", "150000")));

        assertThatThrownBy(() -> debtPaymentService.createPayment(
                3L, new DebtPaymentRequest(new BigDecimal("200000"), null, null))
        ).isInstanceOf(DebtPaymentExceedsBalanceException.class);

        verify(debtRepository, never()).decrementRemainingAmount(any(), any());
        verify(debtPaymentRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    /**
     * Carrera de "lost update": la verificación rápida en memoria pasa (el saldo leído todavía
     * alcanza), pero otro abono concurrente ya consumió el saldo antes de llegar al UPDATE
     * atómico, que devuelve 0 filas. El abono debe rechazarse sin persistir ni el DebtPayment ni
     * el Expense: si se persistieran, el ledger registraría un abono que nunca redujo el saldo.
     */
    @Test
    void createPaymentRejectsWhenTheAtomicUpdateLosesTheRaceAndPersistsNothing() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(3L, 1L))
                .thenReturn(Optional.of(buildDebt(3L, "Préstamo", "1000000", "200000")));
        when(debtRepository.decrementRemainingAmount(3L, new BigDecimal("200000"))).thenReturn(0);

        assertThatThrownBy(() -> debtPaymentService.createPayment(
                3L, new DebtPaymentRequest(new BigDecimal("200000"), null, null))
        ).isInstanceOf(DebtPaymentExceedsBalanceException.class);

        verify(debtPaymentRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createPaymentThrowsNotFoundWhenDebtBelongsToAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtPaymentService.createPayment(
                99L, new DebtPaymentRequest(new BigDecimal("1000"), null, null))
        ).isInstanceOf(DebtNotFoundException.class);

        verify(debtRepository, never()).decrementRemainingAmount(any(), any());
    }

    @Test
    void getPaymentsThrowsNotFoundWhenDebtBelongsToAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtPaymentService.getPayments(99L, org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(DebtNotFoundException.class);
    }

    private Debt buildDebt(Long id, String name, String total, String remaining) {
        Debt debt = new Debt();
        debt.setId(id);
        debt.setName(name);
        debt.setTotalAmount(new BigDecimal(total));
        debt.setRemainingAmount(new BigDecimal(remaining));
        return debt;
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
