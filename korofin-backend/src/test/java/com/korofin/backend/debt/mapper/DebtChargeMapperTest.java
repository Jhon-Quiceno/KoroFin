package com.korofin.backend.debt.mapper;

import com.korofin.backend.debt.dto.DebtChargeRequest;
import com.korofin.backend.debt.dto.DebtChargeResponse;
import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.debt.entity.DebtCharge;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DebtChargeMapperTest {

    private final DebtChargeMapper mapper = Mappers.getMapper(DebtChargeMapper.class);

    @Test
    void toEntityIgnoresIdDebtChargeDateAndCreatedAt() {
        DebtChargeRequest request = new DebtChargeRequest(
                new BigDecimal("18000"), LocalDate.of(2026, 4, 2), "Interés de mora"
        );

        DebtCharge charge = mapper.toEntity(request);

        assertThat(charge.getId()).isNull();
        assertThat(charge.getDebt()).isNull();
        assertThat(charge.getChargeDate()).isNull();
        assertThat(charge.getCreatedAt()).isNull();
        assertThat(charge.getAmount()).isEqualByComparingTo("18000");
        assertThat(charge.getDescription()).isEqualTo("Interés de mora");
    }

    @Test
    void toResponseFlattensDebtId() {
        Debt debt = new Debt();
        debt.setId(6L);
        DebtCharge charge = new DebtCharge();
        charge.setId(21L);
        charge.setDebt(debt);
        charge.setAmount(new BigDecimal("18000"));
        charge.setChargeDate(LocalDate.of(2026, 4, 2));

        DebtChargeResponse response = mapper.toResponse(charge);

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.debtId()).isEqualTo(6L);
        assertThat(response.chargeDate()).isEqualTo(LocalDate.of(2026, 4, 2));
    }
}
