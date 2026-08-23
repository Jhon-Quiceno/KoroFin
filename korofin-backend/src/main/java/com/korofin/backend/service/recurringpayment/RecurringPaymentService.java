package com.korofin.backend.service.recurringpayment;

import com.korofin.backend.dto.recurringpayment.RecurringPaymentPayResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentRequest;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentUpdateRequest;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import com.korofin.backend.entity.recurringpayment.RecurringFrequency;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.exception.recurringpayment.RecurringPaymentAlreadyPaidException;
import com.korofin.backend.exception.recurringpayment.RecurringPaymentNotDueYetException;
import com.korofin.backend.mapper.recurringpayment.RecurringPaymentMapper;
import com.korofin.backend.repository.expense.ExpenseRepository;
import com.korofin.backend.repository.recurringpayment.RecurringPaymentRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Lógica de negocio para administrar los {@link RecurringPayment} del usuario actual.
 *
 * <p>Toda operación resuelve al llamador vía {@link SecurityUtils#getCurrentUserId()} y delimita
 * lecturas/escrituras estrictamente a ese usuario. Las mutaciones sobre un pago recurrente ajeno
 * lanzan {@link ResourceNotFoundException} (HTTP 404), nunca 403.
 *
 * <p>{@link #payRecurringPayment} crea un {@link Expense} vinculado al pago recurrente (ver
 * {@link Expense#getRecurringPayment()}) y recalcula {@code nextPaymentDate} a partir del
 * {@code nextPaymentDate} <em>actual</em> — nunca de {@link LocalDate#now()} — así que una
 * ejecución tardía no comprime el siguiente ciclo. El avance de fecha se aplica primero vía
 * {@link RecurringPaymentRepository#advanceNextPaymentDate} (un {@code UPDATE} condicional
 * atómico, no lectura-y-luego-escritura), así que una llamada duplicada a {@code /pay} (reintento
 * del cliente, doble clic, dos pestañas) no puede pasar de largo este guard y crear un segundo
 * {@link Expense} para el mismo pago lógico — la llamada perdedora recibe
 * {@link RecurringPaymentAlreadyPaidException} (HTTP 409) en su lugar.
 *
 * <p>{@link #payRecurringPayment} también rechaza una llamada hecha antes de que
 * {@code nextPaymentDate} haya llegado con {@link RecurringPaymentNotDueYetException} (HTTP 409).
 * A diferencia del guard de carrera de arriba, esto no es sobre concurrencia: dos clics
 * secuenciales en momentos distintos cada uno lee un {@code nextPaymentDate} fresco, no obsoleto,
 * y de otro modo ambos tendrían éxito, creando un {@link Expense} duplicado cada vez que se
 * presiona el botón.
 */
@Service
public class RecurringPaymentService {

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final RecurringPaymentMapper recurringPaymentMapper;
    private final Clock clock;

    public RecurringPaymentService(
            RecurringPaymentRepository recurringPaymentRepository,
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            RecurringPaymentMapper recurringPaymentMapper,
            Clock clock
    ) {
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.recurringPaymentMapper = recurringPaymentMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<RecurringPaymentResponse> getRecurringPayments(Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return recurringPaymentRepository.findAllByUser_Id(userId, pageable)
                .map(recurringPaymentMapper::toResponse);
    }

    @Transactional
    public RecurringPaymentResponse createRecurringPayment(RecurringPaymentRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        RecurringPayment recurringPayment = recurringPaymentMapper.toEntity(request);
        recurringPayment.setUser(userRepository.getReferenceById(userId));
        recurringPayment.setNextPaymentDate(request.firstPaymentDate());
        recurringPayment.setActive(true);

        RecurringPayment savedRecurringPayment = recurringPaymentRepository.save(recurringPayment);
        return recurringPaymentMapper.toResponse(savedRecurringPayment);
    }

    @Transactional
    public RecurringPaymentResponse updateRecurringPayment(Long recurringPaymentId, RecurringPaymentUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        RecurringPayment recurringPayment = findOwnedRecurringPayment(recurringPaymentId, userId);

        recurringPaymentMapper.updateEntityFromRequest(request, recurringPayment);

        RecurringPayment updatedRecurringPayment = recurringPaymentRepository.save(recurringPayment);
        return recurringPaymentMapper.toResponse(updatedRecurringPayment);
    }

    @Transactional
    public void deleteRecurringPayment(Long recurringPaymentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        RecurringPayment recurringPayment = findOwnedRecurringPayment(recurringPaymentId, userId);
        recurringPaymentRepository.delete(recurringPayment);
    }

    @Transactional
    public RecurringPaymentResponse toggleRecurringPayment(Long recurringPaymentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        RecurringPayment recurringPayment = findOwnedRecurringPayment(recurringPaymentId, userId);

        recurringPayment.setActive(!recurringPayment.isActive());

        RecurringPayment updatedRecurringPayment = recurringPaymentRepository.save(recurringPayment);
        return recurringPaymentMapper.toResponse(updatedRecurringPayment);
    }

    @Transactional
    public RecurringPaymentPayResponse payRecurringPayment(Long recurringPaymentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        RecurringPayment recurringPayment = findOwnedRecurringPayment(recurringPaymentId, userId);

        if (LocalDate.now(clock).isBefore(recurringPayment.getNextPaymentDate())) {
            throw new RecurringPaymentNotDueYetException(
                    "Este servicio vence el " + recurringPayment.getNextPaymentDate()
                            + "; no se puede marcar como pagado antes de esa fecha."
            );
        }

        LocalDate currentNextPaymentDate = recurringPayment.getNextPaymentDate();
        LocalDate newNextPaymentDate = computeNextPaymentDate(recurringPayment);

        int updatedRows = recurringPaymentRepository.advanceNextPaymentDate(
                recurringPaymentId, currentNextPaymentDate, newNextPaymentDate
        );
        if (updatedRows == 0) {
            // Otra llamada concurrente a /pay ya avanzó nextPaymentDate primero; no crear un
            // Expense duplicado para el mismo pago lógico.
            throw new RecurringPaymentAlreadyPaidException("Este servicio ya fue marcado como pagado");
        }

        Expense expense = new Expense();
        expense.setUser(userRepository.getReferenceById(userId));
        expense.setDescription(recurringPayment.getName());
        expense.setAmount(recurringPayment.getAmount());
        expense.setDate(LocalDate.now(clock));
        expense.setPaymentMethod(PaymentMethodType.OTHER);
        expense.setCategory(null);
        expense.setRecurringPayment(recurringPayment);
        Expense savedExpense = expenseRepository.save(expense);

        // El UPDATE atómico de arriba ya persistió la nueva fecha; se mantiene la entidad en
        // memoria sincronizada (en vez de confiar en un nextPaymentDate obsoleto) para que el
        // DTO de respuesta refleje el valor real posterior al avance.
        recurringPayment.setNextPaymentDate(newNextPaymentDate);
        RecurringPayment updatedRecurringPayment = recurringPaymentRepository.save(recurringPayment);

        return new RecurringPaymentPayResponse(
                recurringPaymentMapper.toResponse(updatedRecurringPayment),
                savedExpense.getId()
        );
    }

    private LocalDate computeNextPaymentDate(RecurringPayment recurringPayment) {
        LocalDate currentNextPaymentDate = recurringPayment.getNextPaymentDate();
        return recurringPayment.getFrequency() == RecurringFrequency.MONTHLY
                ? currentNextPaymentDate.plusMonths(1)
                : currentNextPaymentDate.plusWeeks(1);
    }

    private RecurringPayment findOwnedRecurringPayment(Long recurringPaymentId, Long userId) {
        return recurringPaymentRepository.findByIdAndUser_Id(recurringPaymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago recurrente no encontrado"));
    }
}
