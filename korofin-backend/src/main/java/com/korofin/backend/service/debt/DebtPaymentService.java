package com.korofin.backend.service.debt;

import com.korofin.backend.dto.debt.DebtPaymentRequest;
import com.korofin.backend.dto.debt.DebtPaymentResponse;
import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.debt.DebtPayment;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.exception.debt.DebtNotFoundException;
import com.korofin.backend.exception.debt.DebtPaymentExceedsBalanceException;
import com.korofin.backend.mapper.debt.DebtPaymentMapper;
import com.korofin.backend.repository.debt.DebtPaymentRepository;
import com.korofin.backend.repository.debt.DebtRepository;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Lógica de negocio para registrar y listar abonos ({@link DebtPayment}) contra una {@link Debt}
 * del usuario actual.
 *
 * <p>{@link #createPayment} es el único lugar donde {@link Debt#getRemainingAmount()} se
 * decrementa: valida que la deuda sea del usuario actual y después reduce el saldo vía
 * {@link DebtRepository#decrementRemainingAmount} (un {@code UPDATE} atómico condicional, no
 * lectura-y-luego-escritura) antes de persistir el {@link DebtPayment}, de modo que dos abonos
 * concurrentes contra la misma deuda no puedan ambos validar contra el mismo saldo obsoleto y
 * producir un "lost update".
 *
 * <p>{@link #createPayment} además crea un {@link Expense} vinculado al abono (ver
 * {@link Expense#getDebtPayment()}): pagar una deuda sí es una salida de dinero del usuario y
 * debe aparecer en su historial de gastos. Es la diferencia con {@code DebtChargeService}, que no
 * crea ningún gasto porque un cargo solo aumenta lo adeudado.
 */
@Service
public class DebtPaymentService {

    private final DebtPaymentRepository debtPaymentRepository;
    private final DebtRepository debtRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final DebtPaymentMapper debtPaymentMapper;
    private final Clock clock;

    public DebtPaymentService(
            DebtPaymentRepository debtPaymentRepository,
            DebtRepository debtRepository,
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            DebtPaymentMapper debtPaymentMapper,
            Clock clock
    ) {
        this.debtPaymentRepository = debtPaymentRepository;
        this.debtRepository = debtRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.debtPaymentMapper = debtPaymentMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DebtPaymentResponse> getPayments(Long debtId, Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        findOwnedDebt(debtId, userId);

        return debtPaymentRepository.findAllByDebt_IdOrderByPaymentDateDescIdDesc(debtId, pageable)
                .map(debtPaymentMapper::toResponse);
    }

    @Transactional
    public DebtPaymentResponse createPayment(Long debtId, DebtPaymentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Debt debt = findOwnedDebt(debtId, userId);

        // Verificación rápida para dar un error claro en el caso común. NO es la validación real:
        // el remainingAmount leído acá puede quedar obsoleto ante abonos concurrentes, que es
        // exactamente la carrera que cierra el UPDATE atómico de abajo.
        if (request.amount().compareTo(debt.getRemainingAmount()) > 0) {
            throw new DebtPaymentExceedsBalanceException();
        }

        int updatedRows = debtRepository.decrementRemainingAmount(debtId, request.amount());
        if (updatedRows == 0) {
            // Otro abono concurrente consumió el saldo entre la lectura de arriba y este UPDATE
            // atómico; se rechaza sin persistir el DebtPayment.
            throw new DebtPaymentExceedsBalanceException();
        }

        // Relectura después del UPDATE masivo (que limpió el contexto de persistencia): la
        // instancia "debt" de arriba quedó desvinculada y con el saldo viejo, así que se vuelve a
        // cargar una instancia administrada para asociarla al abono.
        Debt updatedDebt = debtRepository.findById(debtId)
                .orElseThrow(DebtNotFoundException::new);

        DebtPayment payment = debtPaymentMapper.toEntity(request);
        payment.setDebt(updatedDebt);
        LocalDate paymentDate = request.paymentDate() != null ? request.paymentDate() : LocalDate.now(clock);
        payment.setPaymentDate(paymentDate);

        DebtPayment savedPayment = debtPaymentRepository.save(payment);

        Expense expense = new Expense();
        expense.setUser(userRepository.getReferenceById(userId));
        expense.setDescription("Abono a deuda: " + updatedDebt.getName());
        expense.setAmount(request.amount());
        expense.setDate(paymentDate);
        expense.setPaymentMethod(PaymentMethodType.OTHER);
        expense.setCategory(null);
        expense.setDebtPayment(savedPayment);
        Expense savedExpense = expenseRepository.save(expense);

        DebtPaymentResponse mappedResponse = debtPaymentMapper.toResponse(savedPayment);
        return new DebtPaymentResponse(
                mappedResponse.id(),
                mappedResponse.debtId(),
                mappedResponse.amount(),
                mappedResponse.paymentDate(),
                mappedResponse.note(),
                mappedResponse.createdAt(),
                savedExpense.getId()
        );
    }

    private Debt findOwnedDebt(Long debtId, Long userId) {
        return debtRepository.findByIdAndUser_Id(debtId, userId)
                .orElseThrow(DebtNotFoundException::new);
    }
}
