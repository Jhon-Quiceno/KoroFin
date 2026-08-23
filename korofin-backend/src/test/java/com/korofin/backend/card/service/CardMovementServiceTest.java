package com.korofin.backend.card.service;

import com.korofin.backend.card.dto.CardMovementResponse;
import com.korofin.backend.card.dto.CardPaymentRequest;
import com.korofin.backend.card.dto.CardPurchaseRequest;
import com.korofin.backend.card.entity.CardMovement;
import com.korofin.backend.card.entity.CardMovementType;
import com.korofin.backend.card.entity.CreditCard;
import com.korofin.backend.card.entity.Installment;
import com.korofin.backend.card.entity.InstallmentPlan;
import com.korofin.backend.card.entity.InstallmentStatus;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.card.exception.CardPaymentExceedsBalanceException;
import com.korofin.backend.card.exception.CardPurchaseOverLimitException;
import com.korofin.backend.card.exception.CreditCardNotFoundException;
import com.korofin.backend.card.exception.InstallmentAmountTooLowException;
import com.korofin.backend.card.exception.InstallmentPlanNotFoundException;
import com.korofin.backend.card.mapper.CardMovementMapper;
import com.korofin.backend.card.mapper.InstallmentMapper;
import com.korofin.backend.card.repository.CardMovementRepository;
import com.korofin.backend.card.repository.CreditCardRepository;
import com.korofin.backend.card.repository.InstallmentPlanRepository;
import com.korofin.backend.card.repository.InstallmentRepository;
import com.korofin.backend.expense.repository.ExpenseRepository;
import com.korofin.backend.user.repository.UserRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardMovementServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CardMovementRepository cardMovementRepository;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CardMovementMapper cardMovementMapper;

    @Mock
    private AmortizationService amortizationService;

    @Mock
    private InstallmentPlanRepository installmentPlanRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private InstallmentMapper installmentMapper;

    private CardMovementService cardMovementService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void buildService() {
        cardMovementService = new CardMovementService(
                cardMovementRepository, creditCardRepository, expenseRepository, userRepository,
                cardMovementMapper, amortizationService, installmentPlanRepository,
                installmentRepository, installmentMapper, FIXED_CLOCK
        );
    }

    /**
     * Una compra simple incrementa el saldo con el UPDATE atómico condicionado al cupo y crea un
     * {@code Expense} vinculado: comprar con tarjeta sí es una salida de dinero del usuario.
     */
    @Test
    void registerSimplePurchaseIncrementsBalanceAtomicallyAndCreatesLinkedExpense() {
        buildService();
        setAuthenticatedUser(1L);
        CardPurchaseRequest request = new CardPurchaseRequest(
                new BigDecimal("250000"), null, "Mercado", null
        );

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        when(creditCardRepository.incrementBalanceWithinLimit(8L, new BigDecimal("250000"))).thenReturn(1);
        when(creditCardRepository.findById(8L)).thenReturn(Optional.of(card(8L, "250000")));
        when(cardMovementMapper.toEntity(request)).thenReturn(new CardMovement());
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(invocation -> {
            CardMovement saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(90L);
            return saved;
        });
        when(cardMovementMapper.toResponse(any(CardMovement.class))).thenReturn(
                mappedResponse(30L, CardMovementType.PURCHASE, "250000")
        );

        CardMovementResponse response = cardMovementService.registerPurchase(8L, request);

        verify(creditCardRepository).incrementBalanceWithinLimit(8L, new BigDecimal("250000"));

        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        Expense createdExpense = expenseCaptor.getValue();
        assertThat(createdExpense.getAmount()).isEqualByComparingTo("250000");
        assertThat(createdExpense.getDescription()).isEqualTo("Compra con tarjeta: Visa Oro");
        assertThat(createdExpense.getPaymentMethod()).isEqualTo(PaymentMethodType.CREDIT_CARD);
        assertThat(createdExpense.getCardMovement().getId()).isEqualTo(30L);

        assertThat(response.expenseId()).isEqualTo(90L);
        assertThat(response.cardBalanceAfter()).isEqualByComparingTo("250000");
        assertThat(response.installmentPlanId()).isNull();

        ArgumentCaptor<CardMovement> movementCaptor = ArgumentCaptor.forClass(CardMovement.class);
        verify(cardMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(CardMovementType.PURCHASE);
        assertThat(movementCaptor.getValue().getDate()).isEqualTo(TODAY);

        verify(installmentPlanRepository, never()).save(any());
    }

    /**
     * Una compra a 2+ cuotas es INSTALLMENT_PURCHASE, persiste el plan con la tasa congelada y
     * TAMBIÉN crea el gasto vinculado por el monto total de la compra.
     */
    @Test
    void registerInstallmentPurchasePersistsThePlanWithTheFrozenRateAndAlsoCreatesTheExpense() {
        buildService();
        setAuthenticatedUser(1L);
        CardPurchaseRequest request = new CardPurchaseRequest(
                new BigDecimal("300000"), null, "TV", 3
        );
        List<Installment> schedule = List.of(installment(1), installment(2), installment(3));

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        // CreditCard no implementa equals(), así que la tarjeta se matchea con any().
        when(amortizationService.buildSchedule(
                org.mockito.ArgumentMatchers.eq(new BigDecimal("300000")),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.0250")),
                org.mockito.ArgumentMatchers.eq(TODAY),
                any(CreditCard.class)))
                .thenReturn(new java.util.ArrayList<>(schedule));
        when(creditCardRepository.incrementBalanceWithinLimit(8L, new BigDecimal("300000"))).thenReturn(1);
        when(creditCardRepository.findById(8L)).thenReturn(Optional.of(card(8L, "300000")));
        when(cardMovementMapper.toEntity(request)).thenReturn(new CardMovement());
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(invocation -> {
            CardMovement saved = invocation.getArgument(0);
            saved.setId(31L);
            return saved;
        });
        when(installmentPlanRepository.save(any(InstallmentPlan.class))).thenAnswer(invocation -> {
            InstallmentPlan saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(91L);
            return saved;
        });
        when(cardMovementMapper.toResponse(any(CardMovement.class))).thenReturn(
                mappedResponse(31L, CardMovementType.INSTALLMENT_PURCHASE, "300000")
        );

        CardMovementResponse response = cardMovementService.registerPurchase(8L, request);

        ArgumentCaptor<InstallmentPlan> planCaptor = ArgumentCaptor.forClass(InstallmentPlan.class);
        verify(installmentPlanRepository).save(planCaptor.capture());
        InstallmentPlan savedPlan = planCaptor.getValue();
        assertThat(savedPlan.getInstallmentCount()).isEqualTo(3);
        assertThat(savedPlan.getRateAtPurchase()).isEqualByComparingTo("0.0250");
        assertThat(savedPlan.getInstallments()).hasSize(3);
        assertThat(savedPlan.getInstallments()).allSatisfy(installment ->
                assertThat(installment.getPlan()).isSameAs(savedPlan));

        ArgumentCaptor<CardMovement> movementCaptor = ArgumentCaptor.forClass(CardMovement.class);
        verify(cardMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(CardMovementType.INSTALLMENT_PURCHASE);

        verify(expenseRepository).save(any(Expense.class));
        assertThat(response.expenseId()).isEqualTo(91L);
        assertThat(response.installmentPlanId()).isEqualTo(12L);
    }

    /** installmentCount = 1 es una compra simple: PURCHASE, sin plan de cuotas. */
    @Test
    void registerPurchaseWithOneInstallmentIsTreatedAsASimplePurchase() {
        buildService();
        setAuthenticatedUser(1L);
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("50000"), null, null, 1);

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        when(creditCardRepository.incrementBalanceWithinLimit(8L, new BigDecimal("50000"))).thenReturn(1);
        when(creditCardRepository.findById(8L)).thenReturn(Optional.of(card(8L, "50000")));
        when(cardMovementMapper.toEntity(request)).thenReturn(new CardMovement());
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));
        when(cardMovementMapper.toResponse(any(CardMovement.class))).thenReturn(
                mappedResponse(32L, CardMovementType.PURCHASE, "50000")
        );

        cardMovementService.registerPurchase(8L, request);

        verify(amortizationService, never()).buildSchedule(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any());
        verify(installmentPlanRepository, never()).save(any());
    }

    /**
     * Rechazo por cupo: el UPDATE atómico devuelve 0 filas (la compra no cabe, o una compra
     * concurrente consumió el cupo). No debe persistirse ni el movimiento ni el gasto.
     */
    @Test
    void registerPurchaseRejectedByTheCreditLimitGuardPersistsNothing() {
        buildService();
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "4900000")));
        when(creditCardRepository.incrementBalanceWithinLimit(8L, new BigDecimal("250000"))).thenReturn(0);

        assertThatThrownBy(() -> cardMovementService.registerPurchase(
                8L, new CardPurchaseRequest(new BigDecimal("250000"), null, null, null))
        ).isInstanceOf(CardPurchaseOverLimitException.class);

        verify(cardMovementRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
        verify(installmentPlanRepository, never()).save(any());
    }

    /**
     * El cronograma se calcula ANTES de tocar el saldo, así que una compra con monto demasiado
     * bajo para la cantidad de cuotas se rechaza con cero efectos secundarios.
     */
    @Test
    void registerInstallmentPurchaseWithTooLowAmountNeverTouchesTheBalance() {
        buildService();
        setAuthenticatedUser(1L);
        CardPurchaseRequest request = new CardPurchaseRequest(new BigDecimal("0.35"), null, null, 48);

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        when(amortizationService.buildSchedule(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any()))
                .thenThrow(new InstallmentAmountTooLowException());

        assertThatThrownBy(() -> cardMovementService.registerPurchase(8L, request))
                .isInstanceOf(InstallmentAmountTooLowException.class);

        verify(creditCardRepository, never()).incrementBalanceWithinLimit(anyLong(), any());
        verify(cardMovementRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    /**
     * Un pago decrementa el saldo y NO crea gasto: ese dinero ya se contabilizó como gasto al
     * hacer la compra, contarlo de nuevo inflaría el total de gastos del usuario.
     */
    @Test
    void registerPaymentDecrementsBalanceAndCreatesNoExpense() {
        buildService();
        setAuthenticatedUser(1L);
        CardPaymentRequest request = new CardPaymentRequest(new BigDecimal("100000"), null, "Pago");

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "250000")));
        when(creditCardRepository.decrementBalance(8L, new BigDecimal("100000"))).thenReturn(1);
        when(creditCardRepository.findById(8L)).thenReturn(Optional.of(card(8L, "150000")));
        when(cardMovementMapper.toEntity(request)).thenReturn(new CardMovement());
        when(cardMovementRepository.save(any(CardMovement.class))).thenAnswer(i -> i.getArgument(0));
        when(cardMovementMapper.toResponse(any(CardMovement.class))).thenReturn(
                mappedResponse(33L, CardMovementType.PAYMENT, "100000")
        );

        CardMovementResponse response = cardMovementService.registerPayment(8L, request);

        verify(creditCardRepository).decrementBalance(8L, new BigDecimal("100000"));
        verify(expenseRepository, never()).save(any());
        assertThat(response.expenseId()).isNull();
        assertThat(response.installmentPlanId()).isNull();
        assertThat(response.cardBalanceAfter()).isEqualByComparingTo("150000");

        ArgumentCaptor<CardMovement> captor = ArgumentCaptor.forClass(CardMovement.class);
        verify(cardMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(CardMovementType.PAYMENT);
        assertThat(captor.getValue().getDate()).isEqualTo(TODAY);
    }

    @Test
    void registerPaymentRejectedByTheBalanceGuardPersistsNothing() {
        buildService();
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "50000")));
        when(creditCardRepository.decrementBalance(8L, new BigDecimal("100000"))).thenReturn(0);

        assertThatThrownBy(() -> cardMovementService.registerPayment(
                8L, new CardPaymentRequest(new BigDecimal("100000"), null, null))
        ).isInstanceOf(CardPaymentExceedsBalanceException.class);

        verify(cardMovementRepository, never()).save(any());
    }

    @Test
    void registerPurchaseThrowsNotFoundWhenCardBelongsToAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardMovementService.registerPurchase(
                99L, new CardPurchaseRequest(new BigDecimal("1000"), null, null, null))
        ).isInstanceOf(CreditCardNotFoundException.class);

        verify(creditCardRepository, never()).incrementBalanceWithinLimit(anyLong(), any());
    }

    @Test
    void registerPaymentThrowsNotFoundWhenCardBelongsToAnotherUser() {
        buildService();
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardMovementService.registerPayment(
                99L, new CardPaymentRequest(new BigDecimal("1000"), null, null))
        ).isInstanceOf(CreditCardNotFoundException.class);

        verify(creditCardRepository, never()).decrementBalance(anyLong(), any());
    }

    @Test
    void getMovementsFiltersByTypeWhenRequested() {
        buildService();
        setAuthenticatedUser(1L);
        var pageable = PageRequest.of(0, 20);
        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        when(cardMovementRepository.findAllByCard_IdAndType(8L, CardMovementType.PURCHASE, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        cardMovementService.getMovements(8L, CardMovementType.PURCHASE, pageable);

        verify(cardMovementRepository).findAllByCard_IdAndType(8L, CardMovementType.PURCHASE, pageable);
        verify(cardMovementRepository, never()).findAllByCard_Id(anyLong(), any());
    }

    @Test
    void getInstallmentsThrowsNotFoundWhenThePlanBelongsToAnotherCard() {
        buildService();
        setAuthenticatedUser(1L);
        CreditCard otherCard = card(77L, "0");
        CardMovement movementOfOtherCard = new CardMovement();
        movementOfOtherCard.setId(30L);
        movementOfOtherCard.setCard(otherCard);
        InstallmentPlan plan = new InstallmentPlan();
        plan.setId(12L);
        plan.setMovement(movementOfOtherCard);

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        when(installmentPlanRepository.findByMovement_Id(30L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> cardMovementService.getInstallments(8L, 30L))
                .isInstanceOf(InstallmentPlanNotFoundException.class);
    }

    @Test
    void getInstallmentsThrowsNotFoundWhenTheMovementHasNoPlan() {
        buildService();
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(card(8L, "0")));
        when(installmentPlanRepository.findByMovement_Id(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardMovementService.getInstallments(8L, 30L))
                .isInstanceOf(InstallmentPlanNotFoundException.class);
    }

    private CardMovementResponse mappedResponse(Long id, CardMovementType type, String amount) {
        return new CardMovementResponse(
                id, 8L, type, new BigDecimal(amount), TODAY, null, null, null, null, null
        );
    }

    private Installment installment(int number) {
        Installment installment = new Installment();
        installment.setNumber(number);
        installment.setCapitalAmount(new BigDecimal("100000"));
        installment.setInterestAmount(new BigDecimal("2500"));
        installment.setDueDate(TODAY.plusMonths(number - 1L));
        installment.setStatus(InstallmentStatus.PENDING);
        return installment;
    }

    private CreditCard card(Long id, String balance) {
        CreditCard card = new CreditCard();
        card.setId(id);
        card.setName("Visa Oro");
        card.setCreditLimit(new BigDecimal("5000000"));
        card.setCurrentBalance(new BigDecimal(balance));
        card.setMonthlyRate(new BigDecimal("0.0250"));
        card.setCutoffDay(15);
        card.setPaymentDueDay(5);
        return card;
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
