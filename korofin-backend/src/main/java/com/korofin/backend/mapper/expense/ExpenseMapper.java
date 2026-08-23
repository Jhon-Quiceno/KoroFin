package com.korofin.backend.mapper.expense;

import com.korofin.backend.dto.expense.ExpenseRequest;
import com.korofin.backend.dto.expense.ExpenseResponse;
import com.korofin.backend.entity.expense.Expense;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link Expense} y sus DTOs de request/response.
 *
 * <p>{@link Expense#getCategory()} se resuelve y asigna aparte por {@code ExpenseService} (debe
 * validarse contra el usuario actual antes de asignarla), así que tanto
 * {@link #toEntity(ExpenseRequest)} como {@link #updateEntityFromRequest(ExpenseRequest, Expense)}
 * la ignoran.
 */
@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Expense toEntity(ExpenseRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ExpenseResponse toResponse(Expense expense);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ExpenseRequest request, @MappingTarget Expense expense);
}
