package com.korofin.backend.entity.card;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan de amortización de capital fijo de una única compra diferida (2 o más cuotas). Es dueño
 * del {@link CardMovement} que la originó (de tipo
 * {@link CardMovementType#INSTALLMENT_PURCHASE}) mediante una FK {@code UNIQUE}.
 *
 * <p>{@link #rateAtPurchase} se congela desde {@link CreditCard#getMonthlyRate()} al momento de
 * la compra y nunca se recalcula, aunque la tasa de la tarjeta cambie después.
 *
 * <p>No hay campo cacheado de capital pendiente: siempre se deriva como
 * {@code SUM(installments.capitalAmount WHERE status = PENDING)}, porque {@link #installments}
 * ya es el ledger auditable por compra.
 */
@Entity
@Table(name = "installment_plans")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstallmentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id", nullable = false, unique = true)
    private CardMovement movement;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    /** Copia congelada de {@link CreditCard#getMonthlyRate()} al momento de crear este plan. */
    @Column(name = "rate_at_purchase", nullable = false, precision = 6, scale = 4)
    private BigDecimal rateAtPurchase;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Installment> installments = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
