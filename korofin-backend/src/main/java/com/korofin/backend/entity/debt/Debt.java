package com.korofin.backend.entity.debt;

import com.korofin.backend.entity.user.User;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una deuda de un {@link User} (por ejemplo un préstamo o un saldo pendiente con un tercero).
 *
 * <p>{@link #remainingAmount} se inicializa desde {@link #totalAmount} al crear la deuda y es el
 * único campo de saldo mutable de esta entidad. Solo puede cambiar a través de un
 * {@code DebtPayment} (lo reduce) o un {@code DebtCharge} (lo incrementa), siempre vía los
 * {@code UPDATE} atómicos de {@code DebtRepository} — nunca se sobrescribe directamente, de modo
 * que todo cambio de saldo queda trazable a un registro concreto.
 *
 * <p>Por eso {@code DebtService#updateDebt} (respaldado por {@code DebtUpdateRequest})
 * deliberadamente no puede tocar ni {@link #totalAmount} ni {@link #remainingAmount}.
 *
 * <p>No hay bandera {@code isActive}/{@code status}: una deuda con {@code remainingAmount = 0}
 * ya está saldada, y ese solo hecho basta para excluirla de cualquier agregación de saldo.
 */
@Entity
@Table(name = "debts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "remaining_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
