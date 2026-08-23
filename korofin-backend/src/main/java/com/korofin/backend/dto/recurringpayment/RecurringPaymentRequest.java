package com.korofin.backend.dto.recurringpayment;

import com.korofin.backend.entity.recurringpayment.RecurringFrequency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload para crear un {@link com.korofin.backend.entity.recurringpayment.RecurringPayment}.
 *
 * @param name             nombre/descripción del pago recurrente
 * @param amount           monto que se cobra cada ciclo, debe ser estrictamente positivo
 * @param frequency        ciclo de facturación
 * @param firstPaymentDate fecha del primer pago programado; siembra {@code nextPaymentDate}
 */
public record RecurringPaymentRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String name,
        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El monto no puede tener más de 2 decimales")
        BigDecimal amount,
        @NotNull(message = "La frecuencia es obligatoria")
        RecurringFrequency frequency,
        @NotNull(message = "La fecha del primer pago es obligatoria")
        LocalDate firstPaymentDate
) {
}
