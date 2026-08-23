package com.korofin.backend.service.statement.dedup;

import com.korofin.backend.dto.statement.MovementType;
import com.korofin.backend.dto.statement.ParsedTransaction;
import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.entity.income.Income;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateDetectorTest {

    private final DuplicateDetector detector = new DuplicateDetector();

    @Test
    void detectsExactMatchAsDuplicate() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Supermercado Exito", BigDecimal.valueOf(187500),
                MovementType.EXPENSE, null, null
        );
        Expense existing = buildExpense(BigDecimal.valueOf(187500), LocalDate.of(2026, 6, 5), "Supermercado Exito");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(existing), List.of());

        assertThat(flags).containsExactly(true);
    }

    @Test
    void detectsDuplicateWithinDateToleranceWindow() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Uber", BigDecimal.valueOf(18900),
                MovementType.EXPENSE, null, null
        );
        Expense existing = buildExpense(BigDecimal.valueOf(18900), LocalDate.of(2026, 6, 7), "Uber");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(existing), List.of());

        assertThat(flags).containsExactly(true);
    }

    @Test
    void doesNotFlagWhenDateDifferenceExceedsWindow() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Uber", BigDecimal.valueOf(18900),
                MovementType.EXPENSE, null, null
        );
        Expense existing = buildExpense(BigDecimal.valueOf(18900), LocalDate.of(2026, 6, 10), "Uber");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(existing), List.of());

        assertThat(flags).containsExactly(false);
    }

    @Test
    void doesNotFlagWhenAmountDiffers() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Uber", BigDecimal.valueOf(18900),
                MovementType.EXPENSE, null, null
        );
        Expense existing = buildExpense(BigDecimal.valueOf(19900), LocalDate.of(2026, 6, 5), "Uber");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(existing), List.of());

        assertThat(flags).containsExactly(false);
    }

    @Test
    void doesNotFlagWhenDescriptionIsUnrelated() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Netflix suscripcion", BigDecimal.valueOf(44900),
                MovementType.EXPENSE, null, null
        );
        Expense existing = buildExpense(BigDecimal.valueOf(44900), LocalDate.of(2026, 6, 5), "Farmacia La Rebaja");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(existing), List.of());

        assertThat(flags).containsExactly(false);
    }

    @Test
    void expenseRowsAreOnlyComparedAgainstExpensesNeverIncomes() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 5), "Salario", BigDecimal.valueOf(4200000),
                MovementType.EXPENSE, null, null
        );
        Income coincidentalIncome = buildIncome(BigDecimal.valueOf(4200000), LocalDate.of(2026, 6, 5), "Salario");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(), List.of(coincidentalIncome));

        assertThat(flags).containsExactly(false);
    }

    @Test
    void incomeRowsAreDetectedAgainstExistingIncomes() {
        ParsedTransaction transaction = new ParsedTransaction(
                LocalDate.of(2026, 6, 1), "Salario agosto", BigDecimal.valueOf(4200000),
                MovementType.INCOME, null, null
        );
        Income existing = buildIncome(BigDecimal.valueOf(4200000), LocalDate.of(2026, 6, 1), "Salario agosto");

        List<Boolean> flags = detector.detectDuplicates(List.of(transaction), List.of(), List.of(existing));

        assertThat(flags).containsExactly(true);
    }

    @Test
    void returnsEmptyListForEmptyTransactionList() {
        assertThat(detector.detectDuplicates(List.of(), List.of(), List.of())).isEmpty();
    }

    private static Expense buildExpense(BigDecimal amount, LocalDate date, String description) {
        Expense expense = new Expense();
        expense.setAmount(amount);
        expense.setDate(date);
        expense.setDescription(description);
        return expense;
    }

    private static Income buildIncome(BigDecimal amount, LocalDate date, String description) {
        Income income = new Income();
        income.setAmount(amount);
        income.setDate(date);
        income.setDescription(description);
        return income;
    }
}
