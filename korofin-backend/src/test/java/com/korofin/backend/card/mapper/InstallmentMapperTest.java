package com.korofin.backend.card.mapper;

import com.korofin.backend.card.dto.InstallmentResponse;
import com.korofin.backend.card.entity.Installment;
import com.korofin.backend.card.entity.InstallmentStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentMapperTest {

    private final InstallmentMapper mapper = Mappers.getMapper(InstallmentMapper.class);

    @Test
    void toResponseMapsEveryFieldByName() {
        Installment installment = new Installment();
        installment.setId(12L);
        installment.setNumber(3);
        installment.setCapitalAmount(new BigDecimal("41666.67"));
        installment.setInterestAmount(new BigDecimal("4166.67"));
        installment.setDueDate(LocalDate.of(2026, 8, 15));
        installment.setStatus(InstallmentStatus.PENDING);

        InstallmentResponse response = mapper.toResponse(installment);

        assertThat(response).isEqualTo(new InstallmentResponse(
                12L, 3, new BigDecimal("41666.67"), new BigDecimal("4166.67"),
                LocalDate.of(2026, 8, 15), InstallmentStatus.PENDING
        ));
    }
}
