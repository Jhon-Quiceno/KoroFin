package com.korofin.backend.mapper.card;

import com.korofin.backend.dto.card.InstallmentResponse;
import com.korofin.backend.entity.card.Installment;
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
