package com.korofin.backend.card.entity;

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
import java.time.LocalDate;

/**
 * Un asiento individual e inmutable del ledger de una {@link CreditCard} (compra, pago, interés
 * materializado, comisión) — la fuente de verdad de la que se deriva
 * {@link CreditCard#getCurrentBalance()}.
 *
 * <p><b>Ledger inmutable:</b> nunca se expone un endpoint de actualización para esta entidad,
 * mismo principio que {@code com.korofin.backend.debt.entity.DebtCharge}/{@code DebtPayment}.
 * Corregir un movimiento equivocado se hace registrando el movimiento inverso, no editando el
 * histórico.
 *
 * <p>{@link #amount} siempre se guarda positivo; el efecto sobre el saldo de la tarjeta
 * (incremento vs. decremento) lo determina {@link #type}, no el signo de {@link #amount}.
 */
@Entity
@Table(name = "card_movements")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CardMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private CreditCard card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private CardMovementType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "movement_date", nullable = false)
    private LocalDate date;

    @Column(length = 255)
    private String description;

    /**
     * Solo se asigna en los movimientos {@link CardMovementType#INTEREST} agregados que crea
     * {@code CycleCloseService}; sirve de rastro de auditoría de qué cierre lo generó.
     * {@code null} en cualquier otro tipo de movimiento.
     */
    @Column(name = "cycle_close_date")
    private LocalDate cycleCloseDate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Referencia inversa al {@link InstallmentPlan} que este movimiento originó (solo se llena
     * cuando {@link #type} es {@link CardMovementType#INSTALLMENT_PURCHASE}). Este lado no posee
     * columna FK — el lado dueño es {@link InstallmentPlan#getMovement()}.
     */
    @OneToOne(mappedBy = "movement", fetch = FetchType.LAZY)
    private InstallmentPlan installmentPlan;
}
