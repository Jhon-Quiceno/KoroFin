package com.korofin.backend.card.mapper;

import com.korofin.backend.card.dto.CardMovementResponse;
import com.korofin.backend.card.dto.CardPaymentRequest;
import com.korofin.backend.card.dto.CardPurchaseRequest;
import com.korofin.backend.card.entity.CardMovement;
import com.korofin.backend.card.entity.CardMovementType;
import com.korofin.backend.card.entity.CreditCard;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CardMovementMapperTest {

    private final CardMovementMapper mapper = Mappers.getMapper(CardMovementMapper.class);

    @Test
    void toEntityFromPurchaseIgnoresCardTypeAndDate() {
        CardPurchaseRequest request = new CardPurchaseRequest(
                new BigDecimal("250000"), LocalDate.of(2026, 6, 1), "Mercado", 6
        );

        CardMovement movement = mapper.toEntity(request);

        assertThat(movement.getId()).isNull();
        assertThat(movement.getCard()).isNull();
        // El tipo lo decide el servidor según el endpoint llamado, nunca el request.
        assertThat(movement.getType()).isNull();
        assertThat(movement.getDate()).isNull();
        assertThat(movement.getCycleCloseDate()).isNull();
        assertThat(movement.getAmount()).isEqualByComparingTo("250000");
        assertThat(movement.getDescription()).isEqualTo("Mercado");
    }

    @Test
    void toEntityFromPaymentIgnoresCardTypeAndDate() {
        CardPaymentRequest request = new CardPaymentRequest(
                new BigDecimal("100000"), LocalDate.of(2026, 6, 5), "Pago mínimo"
        );

        CardMovement movement = mapper.toEntity(request);

        assertThat(movement.getCard()).isNull();
        assertThat(movement.getType()).isNull();
        assertThat(movement.getDate()).isNull();
        assertThat(movement.getAmount()).isEqualByComparingTo("100000");
    }

    @Test
    void toResponseFlattensCardIdAndLeavesDerivedFieldsNull() {
        CreditCard card = new CreditCard();
        card.setId(8L);
        CardMovement movement = new CardMovement();
        movement.setId(30L);
        movement.setCard(card);
        movement.setType(CardMovementType.PURCHASE);
        movement.setAmount(new BigDecimal("250000"));
        movement.setDate(LocalDate.of(2026, 6, 1));

        CardMovementResponse response = mapper.toResponse(movement);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.cardId()).isEqualTo(8L);
        assertThat(response.type()).isEqualTo(CardMovementType.PURCHASE);
        // Los tres derivados los asigna CardMovementService, no vienen de la entidad.
        assertThat(response.cardBalanceAfter()).isNull();
        assertThat(response.expenseId()).isNull();
        assertThat(response.installmentPlanId()).isNull();
    }
}
