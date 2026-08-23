package com.korofin.backend.entity.debt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un abono registrado contra una {@link Debt}.
 *
 * <p>Es el único lugar donde {@link Debt#getRemainingAmount()} se decrementa (ver
 * {@code DebtPaymentService#createPayment}), lo que mantiene toda reducción del saldo trazable a
 * un registro concreto en vez de una sobrescritura silenciosa.
 *
 * <p>Registro inmutable: igual que {@link DebtCharge}, nunca se expone un endpoint de
 * actualización ni de borrado — corregir un abono equivocado se hace registrando el movimiento
 * inverso, no editando el histórico.
 */
@Entity
@Table(name = "debt_payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DebtPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(length = 255)
    private String note;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
