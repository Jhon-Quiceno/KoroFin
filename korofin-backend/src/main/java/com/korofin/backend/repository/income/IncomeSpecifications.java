package com.korofin.backend.repository.income;

import com.korofin.backend.entity.income.Income;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Builders de {@link Specification} dinámicas para los filtros de {@link Income}.
 *
 * <p>Cada método devuelve {@code null} desde su predicado cuando el filtro está ausente, lo que
 * {@link Specification#and(Specification)} interpreta como "sin restricción" — evita bindear
 * parámetros nulos directo en comparaciones JPQL, que falla contra PostgreSQL porque el driver no
 * puede inferir el tipo del parámetro cuando es nulo.
 *
 * <p>{@link #inPeriod} usa una comparación de rango de fechas en vez de
 * {@code EXTRACT(MONTH/YEAR FROM ...)}: es null-safe y puede usar el índice de {@code date}
 * creado en {@code V2__create_categories_expenses_incomes.sql} (EXTRACT() no puede). Si solo se
 * da {@code month} sin {@code year}, se asume el año actual.
 */
public final class IncomeSpecifications {

    private IncomeSpecifications() {
    }

    public static Specification<Income> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Income> inPeriod(Integer month, Integer year) {
        return (root, query, cb) -> {
            if (month == null && year == null) {
                return null;
            }

            int resolvedYear = year != null ? year : LocalDate.now().getYear();
            LocalDate start;
            LocalDate end;
            if (month != null) {
                start = LocalDate.of(resolvedYear, month, 1);
                end = start.plusMonths(1);
            } else {
                start = LocalDate.of(resolvedYear, 1, 1);
                end = start.plusYears(1);
            }

            return cb.and(
                    cb.greaterThanOrEqualTo(root.get("date"), start),
                    cb.lessThan(root.get("date"), end)
            );
        };
    }
}
