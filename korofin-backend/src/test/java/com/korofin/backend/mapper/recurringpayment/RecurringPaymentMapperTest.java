package com.korofin.backend.mapper.recurringpayment;

import com.korofin.backend.dto.recurringpayment.RecurringPaymentRequest;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentUpdateRequest;
import com.korofin.backend.entity.recurringpayment.RecurringFrequency;
import com.korofin.backend.entity.recurringpayment.RecurringPayment;
import com.korofin.backend.entity.user.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringPaymentMapperTest {

    private final RecurringPaymentMapper mapper = Mappers.getMapper(RecurringPaymentMapper.class);

    @Test
    void toEntityIgnoresUserNextPaymentDateAndActive() {
        RecurringPaymentRequest request = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("35000"), RecurringFrequency.MONTHLY, LocalDate.of(2026, 2, 1)
        );

        RecurringPayment entity = mapper.toEntity(request);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getUser()).isNull();
        assertThat(entity.getNextPaymentDate()).isNull();
        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getName()).isEqualTo("Netflix");
        assertThat(entity.getAmount()).isEqualByComparingTo("35000");
        assertThat(entity.getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);
    }

    @Test
    void toResponseMapsActiveToIsActiveJsonField() {
        User owner = new User();
        owner.setId(1L);

        RecurringPayment entity = new RecurringPayment();
        entity.setId(7L);
        entity.setUser(owner);
        entity.setName("Spotify");
        entity.setAmount(new BigDecimal("18900"));
        entity.setFrequency(RecurringFrequency.MONTHLY);
        entity.setNextPaymentDate(LocalDate.of(2026, 3, 1));
        entity.setActive(true);

        RecurringPaymentResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.isActive()).isTrue();
        assertThat(response.nextPaymentDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void updateEntityFromRequestOverwritesScalarFieldsOnly() {
        RecurringPayment entity = new RecurringPayment();
        entity.setId(3L);
        entity.setNextPaymentDate(LocalDate.of(2026, 4, 1));
        entity.setActive(true);

        RecurringPaymentUpdateRequest request = new RecurringPaymentUpdateRequest(
                "Renombrado", new BigDecimal("50000"), RecurringFrequency.WEEKLY
        );
        mapper.updateEntityFromRequest(request, entity);

        assertThat(entity.getId()).isEqualTo(3L);
        assertThat(entity.getName()).isEqualTo("Renombrado");
        assertThat(entity.getAmount()).isEqualByComparingTo("50000");
        assertThat(entity.getFrequency()).isEqualTo(RecurringFrequency.WEEKLY);
        // No tocados por el update:
        assertThat(entity.getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(entity.isActive()).isTrue();
    }
}
