package com.korofin.backend.statement.service.dedup;

import com.korofin.backend.statement.dto.MovementType;
import com.korofin.backend.statement.dto.ParsedTransaction;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.income.entity.Income;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Marca, dentro de una lista de {@link ParsedTransaction} extraídos de un extracto, cuáles
 * probablemente ya están registrados como {@link Expense}/{@link Income} del usuario.
 *
 * <p>Un movimiento se considera duplicado cuando coincide exactamente en monto, la fecha difiere
 * como máximo {@link #MAX_DAY_DIFFERENCE_ALLOWED} días (para tolerar la diferencia entre la fecha
 * de la transacción y la fecha de acreditación bancaria) y la descripción es similar según
 * {@link DescriptionSimilarity#isSimilar}. Las filas {@code EXPENSE} se comparan solo contra
 * gastos existentes, y las {@code INCOME} solo contra ingresos existentes — el llamador
 * ({@code StatementImportService#preview}) es responsable de traer ambas listas ya acotadas a la
 * ventana de fechas relevante.
 */
@Component
public class DuplicateDetector {

    private static final long MAX_DAY_DIFFERENCE_ALLOWED = 3;

    /**
     * @return una lista de booleanos alineada por índice con {@code parsedTransactions}: {@code true}
     *         en la posición {@code i} si {@code parsedTransactions.get(i)} probablemente ya está
     *         registrado en {@code existingExpenses}/{@code existingIncomes}
     */
    public List<Boolean> detectDuplicates(
            List<ParsedTransaction> parsedTransactions,
            List<Expense> existingExpenses,
            List<Income> existingIncomes
    ) {
        List<Boolean> duplicateFlags = new ArrayList<>(parsedTransactions.size());
        for (ParsedTransaction transaction : parsedTransactions) {
            duplicateFlags.add(isDuplicate(transaction, existingExpenses, existingIncomes));
        }
        return duplicateFlags;
    }

    private boolean isDuplicate(
            ParsedTransaction transaction,
            List<Expense> existingExpenses,
            List<Income> existingIncomes
    ) {
        if (transaction.movementType() == MovementType.EXPENSE) {
            return existingExpenses.stream().anyMatch(expense -> matches(
                    transaction, expense.getAmount(), expense.getDate(), expense.getDescription()
            ));
        }
        return existingIncomes.stream().anyMatch(income -> matches(
                transaction, income.getAmount(), income.getDate(), income.getDescription()
        ));
    }

    private boolean matches(ParsedTransaction transaction, BigDecimal amount, LocalDate date, String description) {
        return transaction.amount().compareTo(amount) == 0
                && Math.abs(ChronoUnit.DAYS.between(transaction.date(), date)) <= MAX_DAY_DIFFERENCE_ALLOWED
                && DescriptionSimilarity.isSimilar(transaction.description(), description);
    }
}
