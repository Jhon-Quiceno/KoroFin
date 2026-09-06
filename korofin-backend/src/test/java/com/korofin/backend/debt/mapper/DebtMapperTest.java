package com.korofin.backend.debt.mapper;

import com.korofin.backend.debt.dto.DebtRequest;
import com.korofin.backend.debt.dto.DebtResponse;
import com.korofin.backend.debt.dto.DebtUpdateRequest;
import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DebtMapperTest {

    private final DebtMapper mapper = Mappers.getMapper(DebtMapper.class);

    @Test
    void toEntityIgnoresIdUserRemainingAmountAndTimestamps() {
        DebtRequest request = new DebtRequest(
                "Préstamo auto", new BigDecimal("12000000"), new BigDecimal("1.50"), LocalDate.of(2027, 1, 31)
        );

        Debt debt = mapper.toEntity(request);

        assertThat(debt.getId()).isNull();
        assertThat(debt.getUser()).isNull();
        // remainingAmount lo inicializa DebtService desde totalAmount, no el mapper.
        assertThat(debt.getRemainingAmount()).isNull();
        assertThat(debt.getCreatedAt()).isNull();
        assertThat(debt.getName()).isEqualTo("Préstamo auto");
        assertThat(debt.getTotalAmount()).isEqualByComparingTo("12000000");
        assertThat(debt.getInterestRate()).isEqualByComparingTo("1.50");
    }

    @Test
    void toResponseMapsEveryReadableField() {
        Debt debt = new Debt();
        debt.setId(7L);
        debt.setName("Tarjeta vieja");
        debt.setTotalAmount(new BigDecimal("500000"));
        debt.setRemainingAmount(new BigDecimal("320000"));
        debt.setInterestRate(new BigDecimal("2.10"));
        debt.setDueDate(LocalDate.of(2026, 12, 5));

        DebtResponse response = mapper.toResponse(debt);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Tarjeta vieja");
        assertThat(response.totalAmount()).isEqualByComparingTo("500000");
        assertThat(response.remainingAmount()).isEqualByComparingTo("320000");
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void updateEntityFromRequestNeverTouchesTotalNorRemainingAmount() {
        User owner = new User();
        owner.setId(3L);
        Debt debt = new Debt();
        debt.setId(9L);
        debt.setUser(owner);
        debt.setName("Nombre viejo");
        debt.setTotalAmount(new BigDecimal("1000"));
        debt.setRemainingAmount(new BigDecimal("400"));

        mapper.updateEntityFromRequest(
                new DebtUpdateRequest("Nombre nuevo", new BigDecimal("3.00"), LocalDate.of(2026, 5, 1)),
                debt
        );

        assertThat(debt.getName()).isEqualTo("Nombre nuevo");
        assertThat(debt.getInterestRate()).isEqualByComparingTo("3.00");
        assertThat(debt.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        // Lo importante del test: la actualización no puede reescribir los montos.
        assertThat(debt.getTotalAmount()).isEqualByComparingTo("1000");
        assertThat(debt.getRemainingAmount()).isEqualByComparingTo("400");
        assertThat(debt.getId()).isEqualTo(9L);
        assertThat(debt.getUser()).isSameAs(owner);
    }
}
