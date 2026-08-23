package com.korofin.backend.service.recurringpayment;

import com.korofin.backend.dto.recurringpayment.RecurringPaymentPayResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentRequest;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentUpdateRequest;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.recurringpayment.RecurringFrequency;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.exception.recurringpayment.RecurringPaymentAlreadyPaidException;
import com.korofin.backend.exception.recurringpayment.RecurringPaymentNotDueYetException;
import com.korofin.backend.mapper.recurringpayment.RecurringPaymentMapper;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.recurringpayment.RecurringPaymentRepository;
import com.korofin.backend.repository.user.UserRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringPaymentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T06:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RecurringPaymentRepository recurringPaymentRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecurringPaymentMapper recurringPaymentMapper;

    private RecurringPaymentService service;

    private void buildService() {
        service = new RecurringPaymentService(
                recurringPaymentRepository, expenseRepository, userRepository, recurringPaymentMapper, FIXED_CLOCK
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRecurringPaymentSeedsNextPaymentDateFromFirstPaymentDateAndActivatesIt() {
        buildService();
        setAuthenticatedUser(1L);
        RecurringPaymentRequest request = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("35000"), RecurringFrequency.MONTHLY, LocalDate.of(2026, 7, 1)
        );
        RecurringPayment mapped = new RecurringPayment();
        when(recurringPaymentMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(recurringPaymentRepository.save(mapped)).thenReturn(mapped);
        when(recurringPaymentMapper.toResponse(mapped)).thenReturn(response(5L, LocalDate.of(2026, 7, 1), true));

        service.createRecurringPayment(request);

        assertThat(mapped.getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(mapped.isActive()).isTrue();
        assertThat(mapped.getUser().getId()).isEqualTo(1L);
    }

    @Test
    void toggleRecurringPaymentFlipsTheActiveFlag() {
        buildService();
        setAuthenticatedUser(1L);
        RecurringPayment existing = new RecurringPayment();
        existing.setActive(true);
        when(recurringPaymentRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(existing));
        when(recurringPaymentRepository.save(existing)).thenReturn(existing);
        when(recurringPaymentMapper.toResponse(existing)).thenReturn(response(5L, TODAY, false));

        service.toggleRecurringPayment(5L);

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void updateRecurringPaymentThrowsNotFoundWhenOwnedByAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(recurringPaymentRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRecurringPayment(
                99L, new RecurringPaymentUpdateRequest("X", BigDecimal.TEN, RecurringFrequency.MONTHLY)
        )).isInstanceOf(ResourceNotFoundException.class);
        verify(recurringPaymentRepository, never()).save(any());
    }

    @Test
    void deleteRecurringPaymentDeletesWhenOwnedByCurrentUser() {
        buildService();
        setAuthenticatedUser(2L);
        RecurringPayment payment = new RecurringPayment();
        when(recurringPaymentRepository.findByIdAndUser_Id(8L, 2L)).thenReturn(Optional.of(payment));

        service.deleteRecurringPayment(8L);

        verify(recurringPaymentRepository).delete(payment);
    }

    @Test
    void payRecurringPaymentThrowsNotDueYetWhenNextPaymentDateIsInTheFuture() {
        buildService();
        setAuthenticatedUser(1L);
        RecurringPayment payment = new RecurringPayment();
        payment.setNextPaymentDate(TODAY.plusDays(1));
        when(recurringPaymentRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.payRecurringPayment(5L))
                .isInstanceOf(RecurringPaymentNotDueYetException.class);
        verify(recurringPaymentRepository, never()).advanceNextPaymentDate(any(), any(), any());
    }

    @Test
    void payRecurringPaymentThrowsAlreadyPaidWhenTheAtomicAdvanceLosesTheRace() {
        buildService();
        setAuthenticatedUser(1L);
        RecurringPayment payment = new RecurringPayment();
        payment.setId(5L);
        payment.setNextPaymentDate(TODAY);
        payment.setFrequency(RecurringFrequency.MONTHLY);
        when(recurringPaymentRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(payment));
        when(recurringPaymentRepository.advanceNextPaymentDate(5L, TODAY, TODAY.plusMonths(1))).thenReturn(0);

        assertThatThrownBy(() -> service.payRecurringPayment(5L))
                .isInstanceOf(RecurringPaymentAlreadyPaidException.class);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void payRecurringPaymentCreatesLinkedExpenseAndAdvancesMonthlyFrequency() {
        buildService();
        setAuthenticatedUser(1L);
        RecurringPayment payment = new RecurringPayment();
        payment.setId(5L);
        payment.setName("Netflix");
        payment.setAmount(new BigDecimal("35000"));
        payment.setNextPaymentDate(TODAY);
        payment.setFrequency(RecurringFrequency.MONTHLY);
        when(recurringPaymentRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(payment));
        when(recurringPaymentRepository.advanceNextPaymentDate(5L, TODAY, TODAY.plusMonths(1))).thenReturn(1);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(77L);
            return expense;
        });
        when(recurringPaymentRepository.save(payment)).thenReturn(payment);
        when(recurringPaymentMapper.toResponse(payment)).thenReturn(response(5L, TODAY.plusMonths(1), true));

        RecurringPaymentPayResponse result = service.payRecurringPayment(5L);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getRecurringPayment()).isSameAs(payment);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("35000");
        assertThat(captor.getValue().getDate()).isEqualTo(TODAY);
        assertThat(payment.getNextPaymentDate()).isEqualTo(TODAY.plusMonths(1));
        assertThat(result.expenseId()).isEqualTo(77L);
    }

    @Test
    void payRecurringPaymentAdvancesByOneWeekForWeeklyFrequency() {
        buildService();
        setAuthenticatedUser(1L);
        RecurringPayment payment = new RecurringPayment();
        payment.setId(6L);
        payment.setName("Suscripción semanal");
        payment.setAmount(BigDecimal.TEN);
        payment.setNextPaymentDate(TODAY);
        payment.setFrequency(RecurringFrequency.WEEKLY);
        when(recurringPaymentRepository.findByIdAndUser_Id(6L, 1L)).thenReturn(Optional.of(payment));
        when(recurringPaymentRepository.advanceNextPaymentDate(6L, TODAY, TODAY.plusWeeks(1))).thenReturn(1);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recurringPaymentRepository.save(payment)).thenReturn(payment);
        when(recurringPaymentMapper.toResponse(payment)).thenReturn(response(6L, TODAY.plusWeeks(1), true));

        service.payRecurringPayment(6L);

        assertThat(payment.getNextPaymentDate()).isEqualTo(TODAY.plusWeeks(1));
    }

    private RecurringPaymentResponse response(Long id, LocalDate nextPaymentDate, boolean active) {
        return new RecurringPaymentResponse(id, "Netflix", new BigDecimal("35000"), RecurringFrequency.MONTHLY, nextPaymentDate, active, null, null);
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
