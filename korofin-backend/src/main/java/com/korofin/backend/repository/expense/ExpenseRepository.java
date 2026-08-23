package com.korofin.backend.repository.expense;

import com.korofin.backend.entity.expense.Expense;
import com.korofin.backend.repository.common.MonthlyTotalProjection;
import com.korofin.backend.repository.common.UserLastActivityProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a persistencia de {@link Expense}, siempre delimitado por dueño.
 *
 * <p>Los filtros dinámicos (categoría/rango de fechas/método de pago) se arman vía
 * {@link ExpenseSpecifications} en vez de un {@code @Query} estático, ya que el patrón JPQL
 * {@code :param IS NULL OR ...} falla contra PostgreSQL cuando el parámetro es nulo (el driver no
 * puede inferir su tipo).
 *
 * <p>{@link #findTopCategoriesByUserAndPeriod} y {@link #sumAmountByUserGroupedByMonth} son
 * consultas fijas (usuario + rango de fechas, nunca opcionales) pensadas para alimentar el
 * dominio {@code analysis} de una fase posterior, igual que en FinSmart.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Gastos del usuario en {@code [start, end]} (inclusive), usado por {@code DuplicateDetector}
     * (dominio {@code statement}) para comparar movimientos recién extraídos de un extracto contra
     * los ya registrados en la ventana de fechas relevante.
     */
    List<Expense> findByUser_IdAndDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e "
            + "WHERE e.user.id = :userId AND e.date >= :start AND e.date <= :end")
    BigDecimal sumAmountByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end
    );

    @Query("SELECT e.category.id AS categoryId, e.category.name AS categoryName, SUM(e.amount) AS total "
            + "FROM Expense e WHERE e.user.id = :userId AND e.date >= :start AND e.date <= :end "
            + "GROUP BY e.category.id, e.category.name "
            + "ORDER BY SUM(e.amount) DESC")
    List<CategoryTotalProjection> findTopCategoriesByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end
    );

    /**
     * Totales agrupados por mes calendario en {@code [start, end]}, una fila por mes que tenga al
     * menos un gasto. Los meses sin gastos simplemente están ausentes del resultado; quien la
     * consuma debe completarlos como cero.
     */
    @Query("SELECT YEAR(e.date) AS periodYear, MONTH(e.date) AS periodMonth, SUM(e.amount) AS total "
            + "FROM Expense e WHERE e.user.id = :userId AND e.date >= :start AND e.date <= :end "
            + "GROUP BY YEAR(e.date), MONTH(e.date)")
    List<MonthlyTotalProjection> sumAmountByUserGroupedByMonth(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end
    );

    /**
     * Ids de usuario distintos con al menos un gasto en {@code [start, end]}, respalda a
     * {@code WeeklySummaryJob} (dominio {@code scheduling}). Se usa junto con
     * {@code IncomeRepository#findDistinctUserIdsByDateBetween} para armar el conjunto de
     * usuarios con actividad en el período, sin chequear existencia usuario por usuario.
     */
    @Query("SELECT DISTINCT e.user.id FROM Expense e WHERE e.date >= :start AND e.date <= :end")
    List<Long> findDistinctUserIdsByDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Fecha del gasto más reciente por usuario, entre todos los usuarios, en una sola consulta
     * agrupada. Respalda a {@code InactivityReminderJob} (dominio {@code scheduling}), que
     * combina esto con {@code IncomeRepository#findLatestIncomeDatePerUser()} en memoria en vez
     * de consultar usuario por usuario.
     */
    @Query("SELECT e.user.id AS userId, MAX(e.date) AS lastDate FROM Expense e GROUP BY e.user.id")
    List<UserLastActivityProjection> findLatestExpenseDatePerUser();
}
