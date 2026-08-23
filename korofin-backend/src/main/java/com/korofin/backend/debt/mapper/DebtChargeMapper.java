package com.korofin.backend.debt.mapper;

import com.korofin.backend.debt.dto.DebtChargeRequest;
import com.korofin.backend.debt.dto.DebtChargeResponse;
import com.korofin.backend.debt.entity.DebtCharge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link DebtCharge} y sus DTOs de request/response.
 *
 * <p>{@link DebtCharge#getDebt()} y {@link DebtCharge#getChargeDate()} los resuelve y asigna
 * {@code DebtChargeService} por separado — la deuda debe validarse contra el usuario actual
 * primero, y la fecha del cargo es hoy por defecto cuando se omite en el request.
 */
@Mapper(componentModel = "spring")
public interface DebtChargeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "debt", ignore = true)
    @Mapping(target = "chargeDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    DebtCharge toEntity(DebtChargeRequest request);

    @Mapping(target = "debtId", source = "debt.id")
    DebtChargeResponse toResponse(DebtCharge debtCharge);
}
