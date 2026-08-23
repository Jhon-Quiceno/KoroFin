package com.korofin.backend.entity.expense;

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
 * <p><b>Nota para fases futuras:</b> el {@code Expense} original de FinSmart tiene además tres FKs
 * opcionales hacia {@code recurringPayment}, {@code debtPayment} y {@code cardMovement} (ver
 * {@code docs/backend-plan.md} sección 2.5), agregadas en su momento con migraciones posteriores
 * ({@code V5}, {@code V21}) porque la tabla {@code expenses} ya existía. Los dominios
 * {@code recurringpayment}, {@code debt} y {@code card} todavía no existen en KoroFin (llegan en
 * fases posteriores), así que esta entidad deliberadamente **no** incluye esos tres campos
 * todavía. Cuando se implemente cada uno de esos dominios, su fase debe agregar su propia
 * migración {@code ALTER TABLE expenses ADD COLUMN ...} (FK {@code ON DELETE SET NULL}) y el
 * campo {@code @ManyToOne} correspondiente acá.
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

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
