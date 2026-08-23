package com.korofin.backend.entity.analysis;

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

/**
 * Snapshot persistido del resumen financiero mensual de un {@link User}, calculado por
 * {@code FinancialAnalysisService#getSummary}.
 *
 * <p>{@code FinancialAnalysisService} sigue siendo la fuente de verdad de las cifras — las
 * recalcula desde {@code Expense}/{@code Income} en cada llamada, nunca lee esta tabla para
 * responder — pero deja acá un historial consultable por {@code (usuario, mes calendario)}, único
 * por esa pareja (ver {@code V6__create_financial_analysis.sql}), que se sobrescribe (upsert) cada
 * vez que se vuelve a calcular el mismo período.
 */
@Entity
@Table(name = "financial_analysis")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "total_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expense", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalExpense;

    @Column(name = "total_savings", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSavings;

    /** Porcentaje de ahorro sobre el ingreso del período (0-100), puede ser negativo si hubo déficit. */
    @Column(name = "savings_rate", nullable = false, precision = 6, scale = 2)
    private BigDecimal savingsRate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
