package com.korofin.backend.mapper.debt;

import com.korofin.backend.dto.debt.DebtRequest;
import com.korofin.backend.dto.debt.DebtResponse;
import com.korofin.backend.dto.debt.DebtUpdateRequest;
import com.korofin.backend.entity.debt.Debt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link Debt} y sus DTOs de request/response.
 *
 * <p>{@link Debt#getUser()} lo resuelve y asigna {@code DebtService} por separado.
 * {@link Debt#getRemainingAmount()} lo inicializa {@code DebtService} desde {@code totalAmount}
 * al crear, y {@link #updateEntityFromRequest} nunca lo toca — mapea desde
 * {@link DebtUpdateRequest}, un DTO que deliberadamente no tiene campos de total ni de saldo (ver
 * su Javadoc para el razonamiento de trazabilidad).
 */
@Mapper(componentModel = "spring")
public interface DebtMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Debt toEntity(DebtRequest request);

    DebtResponse toResponse(Debt debt);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(DebtUpdateRequest request, @MappingTarget Debt debt);
}
