package com.korofin.backend.entity.card;

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
 * Una tarjeta de crédito rotativa de un {@link User}.
 *
 * <p>{@link #currentBalance} es la suma cacheada, siempre consistente, de todos los
 * {@link CardMovement} registrados contra esta tarjeta (espejo de
 * {@code com.korofin.backend.entity.debt.Debt#getRemainingAmount()}). Solo puede cambiar a través
 * de los {@code UPDATE} atómicos de {@code CreditCardRepository}, disparados por un
 * {@link CardMovement} concreto — nunca se sobrescribe directamente, de modo que todo cambio de
 * saldo queda trazable a un asiento del ledger.
 *
 * <p>El cupo disponible ({@code creditLimit - currentBalance}) siempre se deriva en tiempo de
 * lectura; nunca se guarda como columna propia, para que no pueda quedar desincronizado.
 */
@Entity
@Table(name = "credit_cards")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String bank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardFranchise franchise;

    @Column(name = "credit_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimit;

    /** Tasa mensual efectiva (por ejemplo {@code 0.0250} para 2,5% E.M.). */
    @Column(name = "monthly_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal monthlyRate;

    @Column(name = "cutoff_day", nullable = false)
    private Integer cutoffDay;

    @Column(name = "payment_due_day", nullable = false)
    private Integer paymentDueDay;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance;

    /**
     * Guard del cierre de ciclo contra cerrar dos veces el mismo ciclo: {@code null} hasta el
     * primer cierre, después la fecha del ciclo cerrado más reciente (ver
     * {@code CreditCardRepository#markCutoffClosed}).
     */
    @Column(name = "last_cutoff_date")
    private LocalDate lastCutoffDate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
