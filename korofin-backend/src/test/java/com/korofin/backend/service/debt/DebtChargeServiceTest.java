package com.korofin.backend.service.debt;

import com.korofin.backend.dto.debt.DebtChargeRequest;
import com.korofin.backend.dto.debt.DebtResponse;
import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.debt.DebtCharge;
import com.korofin.backend.exception.debt.DebtNotFoundException;
import com.korofin.backend.mapper.debt.DebtChargeMapper;
import com.korofin.backend.mapper.debt.DebtMapper;
import com.korofin.backend.repository.debt.DebtChargeRepository;
import com.korofin.backend.repository.debt.DebtRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtChargeServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DebtChargeRepository debtChargeRepository;

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private DebtChargeMapper debtChargeMapper;

    @Mock
    private DebtMapper debtMapper;

    private DebtChargeService debtChargeService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void buildService() {
        debtChargeService = new DebtChargeService(
                debtChargeRepository, debtRepository, debtChargeMapper, debtMapper, FIXED_CLOCK
        );
    }

    /**
     * El incremento tiene que hacerse con el UPDATE atómico del repositorio, nunca leyendo el
     * saldo, sumando en Java y guardando: eso reintroduciría la carrera de "lost update" entre dos
     * cargos concurrentes contra la misma deuda.
     */
    @Test
    void createChargeIncrementsBalanceWithTheAtomicUpdateAndNeverWithASetter() {
        buildService();
        setAuthenticatedUser(1L);
        Debt debtBefore = buildDebt(3L, "500000");
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("30000"), null, "Interés");

        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(debtBefore));
        when(debtRepository.incrementRemainingAmount(3L, new BigDecimal("30000"))).thenReturn(1);
        when(debtRepository.findById(3L)).thenReturn(Optional.of(buildDebt(3L, "530000")));
        when(debtChargeMapper.toEntity(request)).thenReturn(new DebtCharge());
        when(debtChargeRepository.save(any(DebtCharge.class))).thenAnswer(i -> i.getArgument(0));
        when(debtMapper.toResponse(any(Debt.class))).thenAnswer(invocation -> {
            Debt debt = invocation.getArgument(0);
            return new DebtResponse(debt.getId(), "Deuda", new BigDecimal("500000"),
                    debt.getRemainingAmount(), null, null, null, null);
        });

        DebtResponse response = debtChargeService.createCharge(3L, request);

        verify(debtRepository).incrementRemainingAmount(3L, new BigDecimal("30000"));
        // La instancia leída antes del UPDATE conserva el saldo viejo: el servicio no la toca.
        assertThat(debtBefore.getRemainingAmount()).isEqualByComparingTo("500000");
        // La respuesta usa la relectura posterior al UPDATE, con el saldo nuevo.
        assertThat(response.remainingAmount()).isEqualByComparingTo("530000");
    }

    /**
     * El {@code UPDATE} masivo invalida el contexto de persistencia, así que la deuda tiene que
     * releerse DESPUÉS del incremento; si se leyera antes, la respuesta devolvería el saldo viejo.
     */
    @Test
    void createChargeRereadsTheDebtAfterTheBulkUpdateNotBefore() {
        buildService();
        setAuthenticatedUser(1L);
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("30000"), null, null);

        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(buildDebt(3L, "500000")));
        when(debtRepository.incrementRemainingAmount(3L, new BigDecimal("30000"))).thenReturn(1);
        when(debtRepository.findById(3L)).thenReturn(Optional.of(buildDebt(3L, "530000")));
        when(debtChargeMapper.toEntity(request)).thenReturn(new DebtCharge());
        when(debtChargeRepository.save(any(DebtCharge.class))).thenAnswer(i -> i.getArgument(0));
        when(debtMapper.toResponse(any(Debt.class))).thenReturn(
                new DebtResponse(3L, "Deuda", new BigDecimal("500000"), new BigDecimal("530000"), null, null, null, null)
        );

        debtChargeService.createCharge(3L, request);

        var order = inOrder(debtRepository);
        order.verify(debtRepository).incrementRemainingAmount(3L, new BigDecimal("30000"));
        order.verify(debtRepository).findById(3L);
    }

    /** Un cargo aumenta lo adeudado: no es una salida de dinero, así que no genera gasto. */
    @Test
    void createChargeDoesNotCreateAnyLinkedExpense() {
        buildService();
        setAuthenticatedUser(1L);
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("30000"), null, null);
        DebtCharge charge = new DebtCharge();

        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(buildDebt(3L, "500000")));
        when(debtRepository.incrementRemainingAmount(3L, new BigDecimal("30000"))).thenReturn(1);
        when(debtRepository.findById(3L)).thenReturn(Optional.of(buildDebt(3L, "530000")));
        when(debtChargeMapper.toEntity(request)).thenReturn(charge);
        when(debtChargeRepository.save(charge)).thenReturn(charge);
        when(debtMapper.toResponse(any(Debt.class))).thenReturn(
                new DebtResponse(3L, "Deuda", new BigDecimal("500000"), new BigDecimal("530000"), null, null, null, null)
        );

        debtChargeService.createCharge(3L, request);

        // DebtChargeService ni siquiera depende de ExpenseRepository: no puede crear un gasto.
        assertThat(charge.getChargeDate()).isEqualTo(TODAY);
    }

    /**
     * La deuda existía al verificar la pertenencia, pero fue borrada de forma concurrente antes
     * del UPDATE, que devuelve 0 filas. El cargo debe rechazarse sin persistirse: un cargo
     * huérfano que nunca movió el saldo sería un registro falso en el ledger.
     */
    @Test
    void createChargeRejectsWithoutPersistingWhenTheDebtDisappearsBeforeTheUpdate() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(buildDebt(3L, "500000")));
        when(debtRepository.incrementRemainingAmount(3L, new BigDecimal("30000"))).thenReturn(0);

        assertThatThrownBy(() -> debtChargeService.createCharge(
                3L, new DebtChargeRequest(new BigDecimal("30000"), null, null))
        ).isInstanceOf(DebtNotFoundException.class);

        verify(debtChargeRepository, never()).save(any());
    }

    @Test
    void createChargeUsesTheExplicitChargeDateWhenProvided() {
        buildService();
        setAuthenticatedUser(1L);
        LocalDate explicitDate = LocalDate.of(2026, 5, 20);
        DebtChargeRequest request = new DebtChargeRequest(new BigDecimal("30000"), explicitDate, null);

        when(debtRepository.findByIdAndUser_Id(3L, 1L)).thenReturn(Optional.of(buildDebt(3L, "500000")));
        when(debtRepository.incrementRemainingAmount(3L, new BigDecimal("30000"))).thenReturn(1);
        when(debtRepository.findById(3L)).thenReturn(Optional.of(buildDebt(3L, "530000")));
        when(debtChargeMapper.toEntity(request)).thenReturn(new DebtCharge());
        when(debtChargeRepository.save(any(DebtCharge.class))).thenAnswer(i -> i.getArgument(0));
        when(debtMapper.toResponse(any(Debt.class))).thenReturn(
                new DebtResponse(3L, "Deuda", new BigDecimal("500000"), new BigDecimal("530000"), null, null, null, null)
        );

        debtChargeService.createCharge(3L, request);

        ArgumentCaptor<DebtCharge> captor = ArgumentCaptor.forClass(DebtCharge.class);
        verify(debtChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getChargeDate()).isEqualTo(explicitDate);
    }

    @Test
    void createChargeThrowsNotFoundWhenDebtBelongsToAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtChargeService.createCharge(
                99L, new DebtChargeRequest(new BigDecimal("1000"), null, null))
        ).isInstanceOf(DebtNotFoundException.class);

        verify(debtRepository, never()).incrementRemainingAmount(any(), any());
    }

    @Test
    void getChargesThrowsNotFoundWhenDebtBelongsToAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(debtRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtChargeService.getCharges(99L, PageRequest.of(0, 20)))
                .isInstanceOf(DebtNotFoundException.class);
    }

    private Debt buildDebt(Long id, String remaining) {
        Debt debt = new Debt();
        debt.setId(id);
        debt.setName("Deuda");
        debt.setTotalAmount(new BigDecimal("500000"));
        debt.setRemainingAmount(new BigDecimal(remaining));
        return debt;
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
