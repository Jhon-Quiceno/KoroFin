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
 * Un cargo registrado contra una {@link Debt} — la imagen especular de {@link DebtPayment}: en
 * vez de decrementar {@link Debt#getRemainingAmount()}, lo incrementa (intereses acumulados,
 * nuevos consumos sobre la misma deuda, etc.).
 *
 * <p>Es el único lugar donde {@link Debt#getRemainingAmount()} se incrementa (ver
 * {@code DebtChargeService#createCharge}), lo que mantiene todo aumento del saldo trazable a un
 * registro concreto en vez de una sobrescritura silenciosa.
 *
 * <p>A diferencia de un {@link DebtPayment}, un cargo <b>no</b> genera un
 * {@code com.korofin.backend.entity.expense.Expense} vinculado: aumentar el saldo de una deuda no
 * es una salida de dinero del usuario.
 */
@Entity
@Table(name = "debt_charges")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DebtCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

    @Column(length = 255)
    private String description;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
