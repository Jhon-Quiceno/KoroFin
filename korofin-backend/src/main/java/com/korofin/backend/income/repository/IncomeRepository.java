package com.korofin.backend.income.repository;

import com.korofin.backend.income.entity.Income;
import com.korofin.backend.common.repository.MonthlyTotalProjection;
import com.korofin.backend.common.repository.UserLastActivityProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a persistencia de {@link Income}, siempre delimitado por dueño.
 *
 * <p>El filtro de período se arma vía {@link IncomeSpecifications} en vez de un {@code @Query}
 * estático, por la misma razón que {@code ExpenseRepository}: un patrón JPQL
 * {@code :param IS NULL OR ...} falla contra PostgreSQL cuando el parámetro es nulo.
 */
public interface IncomeRepository extends JpaRepository<Income, Long>, JpaSpecificationExecutor<Income> {

    Optional<Income> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Ingresos del usuario en {@code [start, end]} (inclusive), usado por {@code DuplicateDetector}
     * (dominio {@code statement}) para comparar movimientos recién extraídos de un extracto contra
     * los ya registrados en la ventana de fechas relevante.
     */
    List<Income> findByUser_IdAndDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("SELECT i.category.id AS categoryId, i.category.name AS categoryName, SUM(i.amount) AS total "
            + "FROM Income i WHERE i.user.id = :userId AND i.date >= :start AND i.date <= :end "
            + "GROUP BY i.category.id, i.category.name "
            + "ORDER BY SUM(i.amount) DESC")
    List<IncomeCategoryTotalProjection> findTopCategoriesByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end
    );

    /**
     * Totales agrupados por mes calendario en {@code [start, end]}, una fila por mes que tenga al
     * menos un ingreso. Los meses sin ingresos simplemente están ausentes del resultado; quien la
     * consuma debe completarlos como cero.
     */
    @Query("SELECT YEAR(i.date) AS periodYear, MONTH(i.date) AS periodMonth, SUM(i.amount) AS total "
            + "FROM Income i WHERE i.user.id = :userId AND i.date >= :start AND i.date <= :end "
            + "GROUP BY YEAR(i.date), MONTH(i.date)")
    List<MonthlyTotalProjection> sumAmountByUserGroupedByMonth(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end
    );

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i "
            + "WHERE i.user.id = :userId AND i.date >= :start AND i.date <= :end")
    BigDecimal sumAmountByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end
    );

    /**
     * Ids de usuario distintos con al menos un ingreso en {@code [start, end]}, respalda a
     * {@code WeeklySummaryJob} (dominio {@code scheduling}). Ver
     * {@code ExpenseRepository#findDistinctUserIdsByDateBetween} para su contraparte.
     */
    @Query("SELECT DISTINCT i.user.id FROM Income i WHERE i.date >= :start AND i.date <= :end")
    List<Long> findDistinctUserIdsByDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Fecha del ingreso más reciente por usuario, entre todos los usuarios, en una sola consulta
     * agrupada. Respalda a {@code InactivityReminderJob} (dominio {@code scheduling}); ver
     * {@code ExpenseRepository#findLatestExpenseDatePerUser()} para su contraparte.
     */
    @Query("SELECT i.user.id AS userId, MAX(i.date) AS lastDate FROM Income i GROUP BY i.user.id")
    List<UserLastActivityProjection> findLatestIncomeDatePerUser();
}
