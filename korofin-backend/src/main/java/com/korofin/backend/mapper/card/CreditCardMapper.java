package com.korofin.backend.mapper.card;

import com.korofin.backend.dto.card.CreditCardRequest;
import com.korofin.backend.dto.card.CreditCardResponse;
import com.korofin.backend.dto.card.CreditCardUpdateRequest;
import com.korofin.backend.entity.card.CreditCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link CreditCard} y sus DTOs de request/response.
 *
 * <p>{@link CreditCard#getUser()} lo resuelve y asigna {@code CreditCardService} por separado.
 * {@link CreditCard#getCurrentBalance()} lo inicializa {@code CreditCardService} en cero al crear
 * la tarjeta, y {@link #updateEntityFromRequest} nunca lo toca — mapea desde
 * {@link CreditCardUpdateRequest}, un DTO que deliberadamente no tiene franquicia, cupo ni saldo
 * (ver su Javadoc para el razonamiento de trazabilidad).
 *
 * <p>{@link #toResponse} deriva {@code availableCredit} en el momento del mapeo; nunca es una
 * columna guardada, para que no pueda quedar desincronizada del saldo.
 */
@Mapper(componentModel = "spring")
public interface CreditCardMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "currentBalance", ignore = true)
    @Mapping(target = "lastCutoffDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CreditCard toEntity(CreditCardRequest request);

    @Mapping(target = "availableCredit", expression = "java(card.getCreditLimit().subtract(card.getCurrentBalance()))")
    CreditCardResponse toResponse(CreditCard card);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "franchise", ignore = true)
    @Mapping(target = "creditLimit", ignore = true)
    @Mapping(target = "currentBalance", ignore = true)
    @Mapping(target = "lastCutoffDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(CreditCardUpdateRequest request, @MappingTarget CreditCard card);
}
