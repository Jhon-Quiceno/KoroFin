package com.korofin.backend.entity.card;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una cuota individual de un {@link InstallmentPlan}, congelada al momento de la compra por
 * {@code AmortizationService}: {@link #capitalAmount} + {@link #interestAmount} nunca cambian
 * después, y la suma de los capitales de las cuotas {@link InstallmentStatus#PENDING} de un plan
 * es el capital pendiente de esa compra.
 *
 * <p>{@link #status} arranca en {@code PENDING} y pasa a {@code BILLED} exactamente una vez,
 * cuando {@code CycleCloseService} materializa el {@link #interestAmount} de esta cuota en un
 * {@link CardMovement} agregado de tipo {@link CardMovementType#INTEREST}, registrado acá como
 * {@link #interestMovement}.
 */
@Entity
@Table(name = "installments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Installment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private InstallmentPlan plan;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "capital_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal capitalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private InstallmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_movement_id")
    private CardMovement interestMovement;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
