package com.korofin.backend.dto.income;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para crear o actualizar un {@link com.korofin.backend.entity.income.Income}.
 *
 * <p>{@link #date} usa {@link PastOrPresent} en vez de permitir fechas futuras arbitrarias: esta
 * fase modela el ingreso como dinero ya recibido, no un ingreso planeado/programado (eso
 * pertenece a una futura funcionalidad de pagos recurrentes), así que una fecha futura se rechaza
 * como entrada inválida en vez de aceptarse en silencio.
 *
 * @param amount      monto del ingreso, debe ser estrictamente positivo
 * @param description nota libre opcional
 * @param date        fecha en la que se recibió el ingreso, no puede ser futura
 * @param categoryId  identificador opcional de una categoría existente del usuario actual
 */
public record IncomeRequest(
        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor a cero")
        BigDecimal amount,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String description,
        @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate date,
        Long categoryId
) {
}
