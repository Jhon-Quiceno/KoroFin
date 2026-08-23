package com.korofin.backend.ai.dto;

import com.korofin.backend.expense.entity.CategoryType;

import java.math.BigDecimal;

/**
 * Resultado de {@code ReceiptExtractionService#extractFromImage}: si la imagen fue reconocida
 * como un recibo/factura real y, si lo fue, el comercio/descripción, el monto TOTAL, el tipo de
 * movimiento decidido, y (best-effort) la categoría encontrada de ese tipo decidido.
 *
 * @param isReceipt    si la imagen fue reconocida como un recibo o factura real y legible
 * @param description  el nombre del comercio o una descripción breve, o {@code null} cuando
 *                      {@link #isReceipt} es {@code false}
 * @param amount       el monto TOTAL extraído (no una línea individual), o {@code null} cuando
 *                      {@link #isReceipt} es {@code false}
 * @param movementType el tipo de movimiento decidido ({@link CategoryType#EXPENSE} para el caso
 *                      común de un recibo de compra, {@link CategoryType#INCOME} para el caso más
 *                      raro de nota de crédito/reembolso), o {@code null} cuando {@link #isReceipt}
 *                      es {@code false}
 * @param categoryId   identificador de la categoría encontrada, o {@code null} cuando el usuario
 *                      no tiene categorías de {@link #movementType} todavía, no se encontró
 *                      coincidencia, o {@link #isReceipt} es {@code false}
 * @param categoryName nombre de la categoría encontrada, o {@code null} bajo las mismas
 *                      condiciones que {@link #categoryId}
 */
public record ReceiptExtraction(
        boolean isReceipt,
        String description,
        BigDecimal amount,
        CategoryType movementType,
        Long categoryId,
        String categoryName
) {

    /**
     * @return el resultado compartido "no se pudo extraer nada usable de esta imagen":
     *         {@link #isReceipt} {@code false} y cada otro campo {@code null} — usado tanto cuando
     *         el propio modelo reporta {@code isReceipt: false} como cuando su respuesta no se
     *         pudo interpretar en absoluto
     */
    public static ReceiptExtraction notAReceipt() {
        return new ReceiptExtraction(false, null, null, null, null, null);
    }
}
