package com.korofin.backend.analysis.repository;

import com.korofin.backend.analysis.entity.FinancialAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a persistencia de los snapshots {@link FinancialAnalysis}, siempre delimitado por dueño.
 */
public interface FinancialAnalysisRepository extends JpaRepository<FinancialAnalysis, Long> {

    Optional<FinancialAnalysis> findByUser_IdAndPeriodYearAndPeriodMonth(
            Long userId, Integer periodYear, Integer periodMonth
    );
}
