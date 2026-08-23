package com.korofin.backend.dto.expense;

import com.korofin.backend.entity.expense.PaymentMethodType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modelo de lectura de un {@link com.korofin.backend.entity.expense.Expense}.
 *
 * @param id            identificador del gasto
 * @param amount        monto del gasto
 * @param description   nota libre opcional
 * @param date          fecha en la que se pagó el gasto
 * @param paymentMethod método usado para pagar el gasto
 * @param categoryId    identificador de la categoría asociada, o {@code null} si no está clasificado
 * @param categoryName  nombre de la categoría asociada, o {@code null} si no está clasificado
 */
public record ExpenseResponse(
        Long id,
        BigDecimal amount,
        String description,
        LocalDate date,
        PaymentMethodType paymentMethod,
        Long categoryId,
        String categoryName
) {
}
