package com.korofin.backend.dto.statement;

import com.korofin.backend.entity.expense.PaymentMethodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila confirmada por el usuario en {@code POST /api/statement-imports/confirm}: puede venir
 * tal cual se previsualizó, o editada (fecha, monto, descripción, categoría o método de pago
 * corregidos) antes de confirmar.
 *
 * <p>{@link #paymentMethod} solo aplica a movimientos de tipo {@code EXPENSE}; cuando es
 * {@code null}, {@code StatementImportService#confirm} usa {@link PaymentMethodType#OTHER} como
 * valor por defecto (mismo criterio que {@code ExpenseRequest#paymentMethod}, pero sin obligar al
 * usuario a elegirlo fila por fila durante la importación masiva).
 *
 * @param movementType  si la fila se confirma como ingreso o como gasto
 * @param amount        monto del movimiento, debe ser positivo
 * @param date          fecha del movimiento, no puede ser futura
 * @param description   descripción del movimiento
 * @param categoryId    id de una categoría existente del usuario, opcional
 * @param paymentMethod método de pago (solo relevante para gastos); opcional
 */
public record ImportConfirmRow(
        @NotNull(message = "El tipo de movimiento es obligatorio")
        MovementType movementType,
        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor a cero")
        BigDecimal amount,
        @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate date,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description,
        Long categoryId,
        PaymentMethodType paymentMethod
) {
}
