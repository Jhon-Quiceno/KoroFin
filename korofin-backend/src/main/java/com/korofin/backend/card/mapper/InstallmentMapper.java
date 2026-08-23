package com.korofin.backend.card.mapper;

import com.korofin.backend.card.dto.InstallmentResponse;
import com.korofin.backend.card.entity.Installment;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct entre {@link Installment} y {@link InstallmentResponse}. Todos los campos del
 * response coinciden por nombre con los de la entidad, así que no hace falta ningún
 * {@code @Mapping} explícito.
 */
@Mapper(componentModel = "spring")
public interface InstallmentMapper {

    InstallmentResponse toResponse(Installment installment);
}
