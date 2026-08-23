package com.korofin.backend.dto.expense;

import com.korofin.backend.entity.expense.PaymentMethodType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para crear o actualizar un {@link com.korofin.backend.entity.expense.Expense}.
 *
 * <p>{@link #date} usa {@link PastOrPresent} por la misma razón que
 * {@link com.korofin.backend.dto.income.IncomeRequest#date()}: esta fase modela el gasto como
 * dinero ya pagado, no un gasto planeado/programado.
 *
 * @param amount        monto del gasto, debe ser estrictamente positivo
 * @param description   nota libre opcional
 * @param date          fecha en la que se pagó el gasto, no puede ser futura
 * @param paymentMethod método usado para pagar el gasto
 * @param categoryId    identificador opcional de una categoría existente del usuario actual
 */
public record ExpenseRequest(
        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description,
        @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate date,
        @NotNull(message = "El método de pago es obligatorio")
        PaymentMethodType paymentMethod,
        Long categoryId
) {
}
