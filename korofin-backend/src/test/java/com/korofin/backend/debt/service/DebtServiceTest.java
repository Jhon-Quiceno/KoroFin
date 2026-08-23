package com.korofin.backend.debt.service;

import com.korofin.backend.debt.dto.DebtRequest;
import com.korofin.backend.debt.dto.DebtResponse;
import com.korofin.backend.debt.dto.DebtUpdateRequest;
import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.debt.exception.DebtNotFoundException;
import com.korofin.backend.debt.mapper.DebtMapper;
import com.korofin.backend.debt.repository.DebtRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DebtMapper debtMapper;

    @InjectMocks
    private DebtService debtService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDebtSeedsRemainingAmountFromTotalAmount() {
        setAuthenticatedUser(1L);
        DebtRequest request = new DebtRequest("Préstamo", new BigDecimal("1000000"), null, null);
        Debt mappedDebt = new Debt();

        when(debtMapper.toEntity(request)).thenReturn(mappedDebt);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(debtRepository.save(mappedDebt)).thenReturn(mappedDebt);
        when(debtMapper.toResponse(mappedDebt)).thenReturn(response(5L, "1000000", "1000000"));

        DebtResponse created = debtService.createDebt(request);

        assertThat(mappedDebt.getRemainingAmount()).isEqualByComparingTo("1000000");
        assertThat(mappedDebt.getUser().getId()).isEqualTo(1L);
        assertThat(created.remainingAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void updateDebtDelegatesToMapperWhichCannotChangeAmounts() {
        setAuthenticatedUser(1L);
        Debt existing = new Debt();
        existing.setId(5L);
        existing.setTotalAmount(new BigDecimal("1000000"));
        existing.setRemainingAmount(new BigDecimal("600000"));
        DebtUpdateRequest request = new DebtUpdateRequest("Otro nombre", null, null);

        when(debtRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(existing));
        when(debtRepository.save(existing)).thenReturn(existing);
        when(debtMapper.toResponse(existing)).thenReturn(response(5L, "1000000", "600000"));

        DebtResponse updated = debtService.updateDebt(5L, request);

        verify(debtMapper).updateEntityFromRequest(request, existing);
        assertThat(updated.totalAmount()).isEqualByComparingTo("1000000");
        assertThat(updated.remainingAmount()).isEqualByComparingTo("600000");
    }

    @Test
    void updateDebtThrowsNotFoundWhenDebtBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.updateDebt(99L, new DebtUpdateRequest("X", null, null)))
                .isInstanceOf(DebtNotFoundException.class);
        verify(debtRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getDebtThrowsNotFoundWhenDebtBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.getDebt(99L)).isInstanceOf(DebtNotFoundException.class);
    }

    @Test
    void deleteDebtDeletesWhenOwnedByCurrentUser() {
        setAuthenticatedUser(2L);
        Debt debt = new Debt();
        debt.setId(8L);
        when(debtRepository.findByIdAndUser_Id(8L, 2L)).thenReturn(Optional.of(debt));

        debtService.deleteDebt(8L);

        verify(debtRepository).delete(debt);
    }

    @Test
    void deleteDebtThrowsNotFoundWhenOwnedByAnotherUser() {
        setAuthenticatedUser(2L);
        when(debtRepository.findByIdAndUser_Id(8L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.deleteDebt(8L)).isInstanceOf(DebtNotFoundException.class);
        verify(debtRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getDebtsScopesTheQueryToTheCurrentUser() {
        setAuthenticatedUser(4L);
        Pageable pageable = PageRequest.of(0, 20);
        Debt debt = new Debt();
        Page<Debt> page = new PageImpl<>(List.of(debt), pageable, 1);

        when(debtRepository.findAllByUser_Id(4L, pageable)).thenReturn(page);
        when(debtMapper.toResponse(debt)).thenReturn(response(1L, "100", "100"));

        Page<DebtResponse> result = debtService.getDebts(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(debtRepository).findAllByUser_Id(4L, pageable);
    }

    private DebtResponse response(Long id, String total, String remaining) {
        return new DebtResponse(
                id, "Préstamo", new BigDecimal(total), new BigDecimal(remaining),
                null, LocalDate.of(2026, 1, 1), null, null
        );
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
