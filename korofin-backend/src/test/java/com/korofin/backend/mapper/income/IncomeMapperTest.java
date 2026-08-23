package com.korofin.backend.mapper.income;

import com.korofin.backend.dto.income.IncomeRequest;
import com.korofin.backend.dto.income.IncomeResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.income.Income;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeMapperTest {

    private final IncomeMapper mapper = Mappers.getMapper(IncomeMapper.class);

    @Test
    void toResponseMapsCategoryIdAndNameFromNestedCategory() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Salario");

        Income income = new Income();
        income.setId(1L);
        income.setAmount(new BigDecimal("2000000"));
        income.setDescription("Nómina");
        income.setDate(LocalDate.of(2026, 1, 5));
        income.setCategory(category);

        IncomeResponse response = mapper.toResponse(income);

        assertThat(response).isEqualTo(new IncomeResponse(
                1L, new BigDecimal("2000000"), "Nómina", LocalDate.of(2026, 1, 5), 2L, "Salario"
        ));
    }

    @Test
    void toResponseLeavesCategoryFieldsNullWhenUnclassified() {
        Income income = new Income();
        income.setId(1L);
        income.setAmount(new BigDecimal("5000"));
        income.setDate(LocalDate.of(2026, 1, 10));

        IncomeResponse response = mapper.toResponse(income);

        assertThat(response.categoryId()).isNull();
        assertThat(response.categoryName()).isNull();
    }

    @Test
    void toEntityIgnoresIdUserCategoryAndTimestamps() {
        IncomeRequest request = new IncomeRequest(new BigDecimal("1000"), "Nota", LocalDate.of(2026, 1, 1), 9L);

        Income income = mapper.toEntity(request);

        assertThat(income.getId()).isNull();
        assertThat(income.getUser()).isNull();
        assertThat(income.getCategory()).isNull();
        assertThat(income.getCreatedAt()).isNull();
        assertThat(income.getAmount()).isEqualTo(new BigDecimal("1000"));
    }

    @Test
    void updateEntityFromRequestOverwritesScalarFieldsOnly() {
        Income income = new Income();
        income.setId(3L);
        income.setAmount(new BigDecimal("100"));
        Category existingCategory = new Category();
        existingCategory.setId(4L);
        income.setCategory(existingCategory);

        mapper.updateEntityFromRequest(
                new IncomeRequest(new BigDecimal("200"), "Actualizado", LocalDate.of(2026, 2, 1), null),
                income
        );

        assertThat(income.getId()).isEqualTo(3L);
        assertThat(income.getAmount()).isEqualTo(new BigDecimal("200"));
        assertThat(income.getDescription()).isEqualTo("Actualizado");
        assertThat(income.getCategory()).isEqualTo(existingCategory);
    }
}
