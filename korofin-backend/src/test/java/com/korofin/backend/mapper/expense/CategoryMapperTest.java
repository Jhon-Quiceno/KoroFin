package com.korofin.backend.mapper.expense;

import com.korofin.backend.dto.expense.CategoryRequest;
import com.korofin.backend.dto.expense.CategoryResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.CategoryType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    private final CategoryMapper mapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    void toResponseMapsAllExposedFields() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Comida");
        category.setType(CategoryType.EXPENSE);

        CategoryResponse response = mapper.toResponse(category);

        assertThat(response).isEqualTo(new CategoryResponse(1L, "Comida", CategoryType.EXPENSE));
    }

    @Test
    void toEntityIgnoresIdUserAndTimestamps() {
        CategoryRequest request = new CategoryRequest("Transporte", CategoryType.EXPENSE);

        Category category = mapper.toEntity(request);

        assertThat(category.getId()).isNull();
        assertThat(category.getUser()).isNull();
        assertThat(category.getCreatedAt()).isNull();
        assertThat(category.getName()).isEqualTo("Transporte");
        assertThat(category.getType()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    void updateEntityFromRequestOverwritesNameAndType() {
        Category category = new Category();
        category.setId(5L);
        category.setName("Old");
        category.setType(CategoryType.EXPENSE);

        mapper.updateEntityFromRequest(new CategoryRequest("New", CategoryType.INCOME), category);

        assertThat(category.getId()).isEqualTo(5L);
        assertThat(category.getName()).isEqualTo("New");
        assertThat(category.getType()).isEqualTo(CategoryType.INCOME);
    }
}
