package com.korofin.backend.entity.expense;

import com.korofin.backend.entity.card.CardMovement;
import com.korofin.backend.entity.debt.DebtPayment;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
import com.korofin.backend.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un gasto registrado por un {@link User}.
 *
 * <p>{@link #category} es opcional: el usuario puede registrar un gasto sin clasificarlo, y
 * borrar una categoría no borra el historial de gastos que la referenciaban (la FK de base de
 * datos usa {@code ON DELETE SET NULL}, ver {@code V2__create_categories_expenses_incomes.sql}).
 * {@link #paymentMethod} sí es obligatorio.
 *
 * <p>{@link #debtPayment}, {@link #cardMovement} y {@link #recurringPayment} vinculan un gasto
 * generado automáticamente con su origen (un abono a una deuda, una compra con tarjeta, la
 * ejecución de un pago recurrente). Las tres FKs son opcionales y usan {@code ON DELETE SET NULL}
 * (ver {@code V3__add_debt_and_card_domain.sql},
 * {@code V5__create_notification_and_recurring_payment_domains.sql} y
 * {@code docs/backend-plan.md} sección 2.5): borrar el origen no borra el historial del gasto, el
 * gasto solo pierde el vínculo. Ninguna de las tres se asigna desde {@code ExpenseService} — las
 * asignan {@code DebtPaymentService}, {@code CardMovementService} y
 * {@code RecurringPaymentService} al crear el gasto derivado.
 */
@Entity
@Table(name = "expenses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethodType paymentMethod;

    /** Abono a deuda que generó este gasto, o {@code null} si el gasto se registró a mano. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_payment_id")
    private DebtPayment debtPayment;

    /**
     * Movimiento de tarjeta que generó este gasto, o {@code null} si el gasto se registró a mano.
     * Solo lo llenan las compras ({@code PURCHASE}/{@code INSTALLMENT_PURCHASE}); un pago de
     * tarjeta no genera gasto (ya se contabilizó al comprar).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_movement_id")
    private CardMovement cardMovement;

    /** Pago recurrente que generó este gasto, o {@code null} si el gasto se registró a mano. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_payment_id")
    private RecurringPayment recurringPayment;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
