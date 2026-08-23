package com.korofin.backend.mapper.expense;

import com.korofin.backend.dto.expense.ExpenseRequest;
import com.korofin.backend.dto.expense.ExpenseResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.expense.PaymentMethodType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseMapperTest {

    private final ExpenseMapper mapper = Mappers.getMapper(ExpenseMapper.class);

    @Test
    void toResponseMapsCategoryIdAndNameFromNestedCategory() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Comida");

        Expense expense = new Expense();
        expense.setId(1L);
        expense.setAmount(new BigDecimal("15000"));
        expense.setDescription("Almuerzo");
        expense.setDate(LocalDate.of(2026, 1, 10));
        expense.setPaymentMethod(PaymentMethodType.CASH);
        expense.setCategory(category);

        ExpenseResponse response = mapper.toResponse(expense);

        assertThat(response).isEqualTo(new ExpenseResponse(
                1L, new BigDecimal("15000"), "Almuerzo", LocalDate.of(2026, 1, 10),
                PaymentMethodType.CASH, 2L, "Comida"
        ));
    }

    @Test
    void toResponseLeavesCategoryFieldsNullWhenUnclassified() {
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setAmount(new BigDecimal("5000"));
        expense.setDate(LocalDate.of(2026, 1, 10));
        expense.setPaymentMethod(PaymentMethodType.CASH);

        ExpenseResponse response = mapper.toResponse(expense);

        assertThat(response.categoryId()).isNull();
        assertThat(response.categoryName()).isNull();
    }

    @Test
    void toEntityIgnoresIdUserCategoryAndTimestamps() {
        ExpenseRequest request = new ExpenseRequest(
                new BigDecimal("1000"), "Nota", LocalDate.of(2026, 1, 1), PaymentMethodType.TRANSFER, 9L
        );

        Expense expense = mapper.toEntity(request);

        assertThat(expense.getId()).isNull();
        assertThat(expense.getUser()).isNull();
        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getCreatedAt()).isNull();
        assertThat(expense.getAmount()).isEqualTo(new BigDecimal("1000"));
        assertThat(expense.getPaymentMethod()).isEqualTo(PaymentMethodType.TRANSFER);
    }

    @Test
    void updateEntityFromRequestOverwritesScalarFieldsOnly() {
        Expense expense = new Expense();
        expense.setId(3L);
        expense.setAmount(new BigDecimal("100"));
        expense.setPaymentMethod(PaymentMethodType.CASH);
        Category existingCategory = new Category();
        existingCategory.setId(4L);
        expense.setCategory(existingCategory);

        mapper.updateEntityFromRequest(
                new ExpenseRequest(new BigDecimal("200"), "Actualizado", LocalDate.of(2026, 2, 1), PaymentMethodType.OTHER, null),
                expense
        );

        assertThat(expense.getId()).isEqualTo(3L);
        assertThat(expense.getAmount()).isEqualTo(new BigDecimal("200"));
        assertThat(expense.getDescription()).isEqualTo("Actualizado");
        assertThat(expense.getPaymentMethod()).isEqualTo(PaymentMethodType.OTHER);
        // La categoría se ignora en el mapper: sigue intacta, ExpenseService la reasigna aparte.
        assertThat(expense.getCategory()).isEqualTo(existingCategory);
    }
}
