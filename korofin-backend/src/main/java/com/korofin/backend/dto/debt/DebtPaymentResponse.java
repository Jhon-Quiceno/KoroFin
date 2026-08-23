package com.korofin.backend.dto.debt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Modelo de lectura de un {@link com.korofin.backend.entity.debt.DebtPayment}.
 *
 * @param id          identificador del abono
 * @param debtId      identificador de la deuda a la que se aplicó el abono
 * @param amount      monto del abono
 * @param paymentDate fecha del abono
 * @param note        nota libre opcional
 * @param createdAt   marca de tiempo de creación
 * @param expenseId   identificador del {@code Expense} creado junto con este abono (ver
 *                    {@code DebtPaymentService#createPayment}). Queda {@code null} al listar
 *                    abonos históricos: el vínculo vive en {@code Expense}, no en el abono, y
 *                    resolverlo en el listado costaría una consulta por fila
 */
public record DebtPaymentResponse(
        Long id,
        Long debtId,
        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        Instant createdAt,
        Long expenseId
) {
}
