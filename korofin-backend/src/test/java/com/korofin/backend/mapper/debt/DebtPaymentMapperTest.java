package com.korofin.backend.mapper.debt;

import com.korofin.backend.dto.debt.DebtPaymentRequest;
import com.korofin.backend.dto.debt.DebtPaymentResponse;
import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.debt.DebtPayment;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DebtPaymentMapperTest {

    private final DebtPaymentMapper mapper = Mappers.getMapper(DebtPaymentMapper.class);

    @Test
    void toEntityIgnoresIdDebtPaymentDateAndCreatedAt() {
        DebtPaymentRequest request = new DebtPaymentRequest(
                new BigDecimal("50000"), LocalDate.of(2026, 3, 1), "Abono extra"
        );

        DebtPayment payment = mapper.toEntity(request);

        assertThat(payment.getId()).isNull();
        assertThat(payment.getDebt()).isNull();
        // La fecha la resuelve el servicio (hoy por defecto), no el mapper.
        assertThat(payment.getPaymentDate()).isNull();
        assertThat(payment.getCreatedAt()).isNull();
        assertThat(payment.getAmount()).isEqualByComparingTo("50000");
        assertThat(payment.getNote()).isEqualTo("Abono extra");
    }

    @Test
    void toResponseFlattensDebtIdAndLeavesExpenseIdNull() {
        Debt debt = new Debt();
        debt.setId(4L);
        DebtPayment payment = new DebtPayment();
        payment.setId(11L);
        payment.setDebt(debt);
        payment.setAmount(new BigDecimal("50000"));
        payment.setPaymentDate(LocalDate.of(2026, 3, 1));
        payment.setNote("Abono extra");

        DebtPaymentResponse response = mapper.toResponse(payment);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.debtId()).isEqualTo(4L);
        assertThat(response.amount()).isEqualByComparingTo("50000");
        // expenseId lo asigna DebtPaymentService después, no viene de la entidad.
        assertThat(response.expenseId()).isNull();
    }
}
