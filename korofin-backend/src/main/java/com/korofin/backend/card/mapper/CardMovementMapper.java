package com.korofin.backend.card.mapper;

import com.korofin.backend.card.dto.CardMovementResponse;
import com.korofin.backend.card.dto.CardPaymentRequest;
import com.korofin.backend.card.dto.CardPurchaseRequest;
import com.korofin.backend.card.entity.CardMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link CardMovement} y sus DTOs de request/response.
 *
 * <p>{@link CardMovement#getCard()}, {@link CardMovement#getType()} y
 * {@link CardMovement#getDate()} los resuelve y asigna {@code CardMovementService} por separado:
 * la tarjeta debe validarse contra el usuario actual primero, el tipo depende de qué endpoint se
 * llamó (compra o pago) y nunca del cliente, y la fecha es hoy por defecto cuando se omite.
 *
 * <p>Los campos derivados de {@link CardMovementResponse} ({@code cardBalanceAfter},
 * {@code expenseId}, {@code installmentPlanId}) también los asigna el servicio aparte, ya que no
 * vienen de la entidad {@link CardMovement} en sí.
 */
@Mapper(componentModel = "spring")
public interface CardMovementMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "card", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "date", ignore = true)
    @Mapping(target = "cycleCloseDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "installmentPlan", ignore = true)
    CardMovement toEntity(CardPurchaseRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "card", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "date", ignore = true)
    @Mapping(target = "cycleCloseDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "installmentPlan", ignore = true)
    CardMovement toEntity(CardPaymentRequest request);

    @Mapping(target = "cardId", source = "card.id")
    @Mapping(target = "cardBalanceAfter", ignore = true)
    @Mapping(target = "expenseId", ignore = true)
    @Mapping(target = "installmentPlanId", ignore = true)
    CardMovementResponse toResponse(CardMovement movement);
}
