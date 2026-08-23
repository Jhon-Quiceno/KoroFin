package com.korofin.backend.expense.repository;

import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Builders de {@link Specification} dinámicas para los filtros de {@link Expense}.
 *
 * <p>Cada método devuelve {@code null} desde su predicado cuando el filtro está ausente, lo que
 * {@link Specification#and(Specification)} interpreta como "sin restricción". Esto evita bindear
 * parámetros nulos directo en comparaciones JPQL (p. ej.
 * {@code :paymentMethod IS NULL OR e.paymentMethod = :paymentMethod}), que falla contra
 * PostgreSQL porque el driver no puede inferir el tipo del parámetro cuando es nulo.
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Expense> hasCategory(Long categoryId) {
        return (root, query, cb) -> categoryId == null
                ? null
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Expense> dateFrom(LocalDate from) {
        return (root, query, cb) -> from == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<Expense> dateTo(LocalDate to) {
        return (root, query, cb) -> to == null
                ? null
                : cb.lessThanOrEqualTo(root.get("date"), to);
    }

    public static Specification<Expense> hasPaymentMethod(PaymentMethodType paymentMethod) {
        return (root, query, cb) -> paymentMethod == null
                ? null
                : cb.equal(root.get("paymentMethod"), paymentMethod);
    }
}
