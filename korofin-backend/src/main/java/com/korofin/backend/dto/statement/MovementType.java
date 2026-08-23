package com.korofin.backend.dto.statement;

/**
 * Clasifica un movimiento extraído de un extracto bancario como dinero que entra o que sale.
 *
 * <p>No se persiste directamente: en la confirmación de la importación, cada fila se convierte en
 * un {@code Income} o un {@code Expense} según este valor (ver
 * {@code StatementImportService#confirm}).
 */
public enum MovementType {
    INCOME,
    EXPENSE
}
