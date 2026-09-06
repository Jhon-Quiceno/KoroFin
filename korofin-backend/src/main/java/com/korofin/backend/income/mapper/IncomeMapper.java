package com.korofin.backend.income.mapper;

import com.korofin.backend.income.dto.IncomeRequest;
import com.korofin.backend.income.dto.IncomeResponse;
import com.korofin.backend.income.entity.Income;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link Income} y sus DTOs de request/response.
 *
 * <p>{@link Income#getCategory()} se resuelve y asigna aparte por {@code IncomeService} (debe
 * validarse contra el usuario actual antes de asignarla), así que tanto
 * {@link #toEntity(IncomeRequest)} como {@link #updateEntityFromRequest(IncomeRequest, Income)}
 * la ignoran.
 */
@Mapper(componentModel = "spring")
public interface IncomeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Income toEntity(IncomeRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    IncomeResponse toResponse(Income income);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(IncomeRequest request, @MappingTarget Income income);
}
