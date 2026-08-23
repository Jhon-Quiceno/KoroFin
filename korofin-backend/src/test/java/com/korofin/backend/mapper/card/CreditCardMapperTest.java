package com.korofin.backend.mapper.card;

import com.korofin.backend.dto.card.CreditCardRequest;
import com.korofin.backend.dto.card.CreditCardResponse;
import com.korofin.backend.dto.card.CreditCardUpdateRequest;
import com.korofin.backend.entity.card.CardFranchise;
import com.korofin.backend.entity.card.CreditCard;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CreditCardMapperTest {

    private final CreditCardMapper mapper = Mappers.getMapper(CreditCardMapper.class);

    @Test
    void toEntityIgnoresIdUserBalanceCutoffDateAndTimestamps() {
        CreditCardRequest request = new CreditCardRequest(
                "Visa Oro", "Bancolombia", CardFranchise.VISA,
                new BigDecimal("5000000"), new BigDecimal("0.0250"), 15, 5
        );

        CreditCard card = mapper.toEntity(request);

        assertThat(card.getId()).isNull();
        assertThat(card.getUser()).isNull();
        // currentBalance lo inicializa CreditCardService en cero, no el mapper.
        assertThat(card.getCurrentBalance()).isNull();
        assertThat(card.getLastCutoffDate()).isNull();
        assertThat(card.getName()).isEqualTo("Visa Oro");
        assertThat(card.getFranchise()).isEqualTo(CardFranchise.VISA);
        assertThat(card.getCutoffDay()).isEqualTo(15);
    }

    @Test
    void toResponseDerivesAvailableCreditFromLimitMinusBalance() {
        CreditCard card = buildCard(new BigDecimal("5000000"), new BigDecimal("1200000"));
        card.setLastCutoffDate(LocalDate.of(2026, 5, 15));

        CreditCardResponse response = mapper.toResponse(card);

        assertThat(response.currentBalance()).isEqualByComparingTo("1200000");
        assertThat(response.availableCredit()).isEqualByComparingTo("3800000");
        assertThat(response.lastCutoffDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void toResponseReportsFullAvailableCreditOnCardWithoutMovements() {
        CreditCardResponse response = mapper.toResponse(buildCard(new BigDecimal("5000000"), BigDecimal.ZERO));

        assertThat(response.availableCredit()).isEqualByComparingTo("5000000");
    }

    @Test
    void updateEntityFromRequestNeverTouchesFranchiseLimitNorBalance() {
        CreditCard card = buildCard(new BigDecimal("5000000"), new BigDecimal("1200000"));
        card.setId(3L);
        card.setName("Nombre viejo");

        mapper.updateEntityFromRequest(
                new CreditCardUpdateRequest("Nombre nuevo", "Davivienda", new BigDecimal("0.0300"), 20, 10),
                card
        );

        assertThat(card.getName()).isEqualTo("Nombre nuevo");
        assertThat(card.getBank()).isEqualTo("Davivienda");
        assertThat(card.getMonthlyRate()).isEqualByComparingTo("0.0300");
        assertThat(card.getCutoffDay()).isEqualTo(20);
        // Lo importante del test: la actualización no puede reescribir franquicia, cupo ni saldo.
        assertThat(card.getFranchise()).isEqualTo(CardFranchise.VISA);
        assertThat(card.getCreditLimit()).isEqualByComparingTo("5000000");
        assertThat(card.getCurrentBalance()).isEqualByComparingTo("1200000");
        assertThat(card.getId()).isEqualTo(3L);
    }

    private CreditCard buildCard(BigDecimal creditLimit, BigDecimal currentBalance) {
        CreditCard card = new CreditCard();
        card.setName("Visa Oro");
        card.setFranchise(CardFranchise.VISA);
        card.setCreditLimit(creditLimit);
        card.setCurrentBalance(currentBalance);
        card.setMonthlyRate(new BigDecimal("0.0250"));
        card.setCutoffDay(15);
        card.setPaymentDueDay(5);
        return card;
    }
}
