package com.korofin.backend.service.card;

import com.korofin.backend.dto.card.CardMovementResponse;
import com.korofin.backend.dto.card.CardPaymentRequest;
import com.korofin.backend.dto.card.CardPurchaseRequest;
import com.korofin.backend.dto.card.InstallmentResponse;
import com.korofin.backend.entity.card.CardMovement;
import com.korofin.backend.entity.card.CardMovementType;
import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.entity.card.Installment;
import com.korofin.backend.entity.card.InstallmentPlan;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.exception.card.CardPaymentExceedsBalanceException;
import com.korofin.backend.exception.card.CardPurchaseOverLimitException;
import com.korofin.backend.exception.card.CreditCardNotFoundException;
import com.korofin.backend.exception.card.InstallmentPlanNotFoundException;
import com.korofin.backend.mapper.card.CardMovementMapper;
import com.korofin.backend.mapper.card.InstallmentMapper;
import com.korofin.backend.repository.card.CardMovementRepository;
import com.korofin.backend.repository.card.CreditCardRepository;
import com.korofin.backend.repository.card.InstallmentPlanRepository;
import com.korofin.backend.repository.card.InstallmentRepository;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Lógica de negocio para registrar y listar movimientos ({@link CardMovement}) contra una
 * {@link CreditCard} del usuario actual.
 *
 * <p>{@link #registerPurchase} es el único lugar donde {@link CreditCard#getCurrentBalance()} se
 * incrementa por una compra: valida que la tarjeta sea del usuario actual y después incrementa el
 * saldo vía {@link CreditCardRepository#incrementBalanceWithinLimit} (un {@code UPDATE} atómico
 * condicionado al cupo, no lectura-y-luego-escritura) antes de persistir el {@link CardMovement},
 * de modo que dos compras concurrentes no puedan ambas validar contra el mismo cupo obsoleto.
 *
 * <p><b>Una compra siempre crea un {@link Expense} vinculado</b> (ver
 * {@link Expense#getCardMovement()}), tanto la simple como la diferida a cuotas: comprar con
 * tarjeta es una salida de dinero que debe aparecer en el historial de gastos del usuario, igual
 * que un abono a una deuda. Las compras de 2 o más cuotas además delegan el cronograma a
 * {@link AmortizationService} y persisten el {@link InstallmentPlan} resultante con la tasa de la
 * tarjeta congelada al momento de la compra.
 *
 * <p>{@link #registerPayment} es el espejo simétrico: decrementa el saldo vía
 * {@link CreditCardRepository#decrementBalance} y <b>no</b> crea ningún {@link Expense} — pagar
 * la tarjeta no es un gasto nuevo, ese dinero ya se contabilizó como gasto cuando se hizo la
 * compra. Contarlo dos veces inflaría el total de gastos del usuario.
 */
@Service
public class CardMovementService {

    private final CardMovementRepository cardMovementRepository;
    private final CreditCardRepository creditCardRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CardMovementMapper cardMovementMapper;
    private final AmortizationService amortizationService;
    private final InstallmentPlanRepository installmentPlanRepository;
    private final InstallmentRepository installmentRepository;
    private final InstallmentMapper installmentMapper;
    private final Clock clock;

    public CardMovementService(
            CardMovementRepository cardMovementRepository,
            CreditCardRepository creditCardRepository,
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            CardMovementMapper cardMovementMapper,
            AmortizationService amortizationService,
            InstallmentPlanRepository installmentPlanRepository,
            InstallmentRepository installmentRepository,
            InstallmentMapper installmentMapper,
            Clock clock
    ) {
        this.cardMovementRepository = cardMovementRepository;
        this.creditCardRepository = creditCardRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.cardMovementMapper = cardMovementMapper;
        this.amortizationService = amortizationService;
        this.installmentPlanRepository = installmentPlanRepository;
        this.installmentRepository = installmentRepository;
        this.installmentMapper = installmentMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CardMovementResponse> getMovements(Long cardId, CardMovementType type, Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        findOwnedCard(cardId, userId);

        Page<CardMovement> movements = type != null
                ? cardMovementRepository.findAllByCard_IdAndType(cardId, type, pageable)
                : cardMovementRepository.findAllByCard_Id(cardId, pageable);

        // cardBalanceAfter/expenseId/installmentPlanId quedan null en el listado: el saldo actual
        // de la tarjeta no representa el saldo justo después de un movimiento histórico, y
        // resolver los otros dos costaría una consulta por fila.
        return movements.map(cardMovementMapper::toResponse);
    }

    @Transactional
    public CardMovementResponse registerPurchase(Long cardId, CardPurchaseRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CreditCard card = findOwnedCard(cardId, userId);

        boolean isInstallmentPurchase = request.installmentCount() != null && request.installmentCount() >= 2;
        LocalDate purchaseDate = request.date() != null ? request.date() : LocalDate.now(clock);

        // El cronograma se calcula ANTES de tocar el saldo o persistir nada: si el monto es
        // demasiado bajo para la cantidad de cuotas elegida, la compra se rechaza con cero
        // efectos secundarios, igual que el rechazo por cupo insuficiente de más abajo.
        List<Installment> schedule = isInstallmentPurchase
                ? amortizationService.buildSchedule(
                        request.amount(), request.installmentCount(), card.getMonthlyRate(), purchaseDate, card)
                : null;

        int updatedRows = creditCardRepository.incrementBalanceWithinLimit(cardId, request.amount());
        if (updatedRows == 0) {
            // O bien la compra supera el cupo, o bien otra compra concurrente lo consumió entre
            // la lectura de la tarjeta y este UPDATE atómico; se rechaza sin persistir nada.
            throw new CardPurchaseOverLimitException();
        }

        // Relectura después del UPDATE masivo (que limpió el contexto de persistencia): se
        // necesita una instancia administrada y con el saldo nuevo, tanto para asociarla al
        // movimiento como para devolver cardBalanceAfter.
        CreditCard cardAfter = findCardById(cardId);

        CardMovement movement = cardMovementMapper.toEntity(request);
        movement.setCard(cardAfter);
        movement.setType(isInstallmentPurchase ? CardMovementType.INSTALLMENT_PURCHASE : CardMovementType.PURCHASE);
        movement.setDate(purchaseDate);
        CardMovement savedMovement = cardMovementRepository.save(movement);

        Long installmentPlanId = null;
        if (isInstallmentPurchase) {
            InstallmentPlan plan = new InstallmentPlan();
            plan.setMovement(savedMovement);
            plan.setInstallmentCount(request.installmentCount());
            // Tasa vigente de la tarjeta ahora mismo, congelada: si monthlyRate cambia después,
            // este plan conserva el valor de hoy sin recalcular ninguna cuota.
            plan.setRateAtPurchase(cardAfter.getMonthlyRate());
            schedule.forEach(installment -> installment.setPlan(plan));
            plan.setInstallments(schedule);
            InstallmentPlan savedPlan = installmentPlanRepository.save(plan);
            installmentPlanId = savedPlan.getId();
        }

        Expense expense = new Expense();
        expense.setUser(userRepository.getReferenceById(userId));
        expense.setDescription("Compra con tarjeta: " + cardAfter.getName());
        expense.setAmount(request.amount());
        expense.setDate(purchaseDate);
        expense.setPaymentMethod(PaymentMethodType.CREDIT_CARD);
        expense.setCategory(null);
        expense.setCardMovement(savedMovement);
        Expense savedExpense = expenseRepository.save(expense);

        CardMovementResponse mappedResponse = cardMovementMapper.toResponse(savedMovement);
        return new CardMovementResponse(
                mappedResponse.id(),
                mappedResponse.cardId(),
                mappedResponse.type(),
                mappedResponse.amount(),
                mappedResponse.date(),
                mappedResponse.description(),
                cardAfter.getCurrentBalance(),
                savedExpense.getId(),
                installmentPlanId,
                mappedResponse.createdAt()
        );
    }

    @Transactional
    public CardMovementResponse registerPayment(Long cardId, CardPaymentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        findOwnedCard(cardId, userId);

        int updatedRows = creditCardRepository.decrementBalance(cardId, request.amount());
        if (updatedRows == 0) {
            // O bien el pago supera el saldo, o bien otro pago concurrente ya lo redujo entre la
            // lectura de la tarjeta y este UPDATE atómico; se rechaza sin persistir nada.
            throw new CardPaymentExceedsBalanceException();
        }

        CreditCard cardAfter = findCardById(cardId);
        LocalDate paymentDate = request.date() != null ? request.date() : LocalDate.now(clock);

        CardMovement movement = cardMovementMapper.toEntity(request);
        movement.setCard(cardAfter);
        movement.setType(CardMovementType.PAYMENT);
        movement.setDate(paymentDate);
        CardMovement savedMovement = cardMovementRepository.save(movement);

        CardMovementResponse mappedResponse = cardMovementMapper.toResponse(savedMovement);
        return new CardMovementResponse(
                mappedResponse.id(),
                mappedResponse.cardId(),
                mappedResponse.type(),
                mappedResponse.amount(),
                mappedResponse.date(),
                mappedResponse.description(),
                cardAfter.getCurrentBalance(),
                // Un pago de tarjeta no genera Expense ni plan de cuotas.
                null,
                null,
                mappedResponse.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public List<InstallmentResponse> getInstallments(Long cardId, Long movementId) {
        Long userId = SecurityUtils.getCurrentUserId();
        findOwnedCard(cardId, userId);

        InstallmentPlan plan = installmentPlanRepository.findByMovement_Id(movementId)
                // El filtro por tarjeta evita que el dueño de una tarjeta lea el plan de otra
                // tarjeta (propia o ajena) pasando un movementId que no le corresponde.
                .filter(candidate -> candidate.getMovement().getCard().getId().equals(cardId))
                .orElseThrow(InstallmentPlanNotFoundException::new);

        return installmentRepository.findAllByPlan_IdOrderByNumber(plan.getId())
                .stream()
                .map(installmentMapper::toResponse)
                .toList();
    }

    private CreditCard findOwnedCard(Long cardId, Long userId) {
        return creditCardRepository.findByIdAndUser_Id(cardId, userId)
                .orElseThrow(CreditCardNotFoundException::new);
    }

    private CreditCard findCardById(Long cardId) {
        return creditCardRepository.findById(cardId)
                .orElseThrow(CreditCardNotFoundException::new);
    }
}
