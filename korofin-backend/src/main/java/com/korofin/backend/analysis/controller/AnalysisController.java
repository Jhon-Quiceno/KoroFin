package com.korofin.backend.analysis.controller;

import com.korofin.backend.analysis.dto.FinancialSummaryResponse;
import com.korofin.backend.analysis.dto.MonthEndPredictionResponse;
import com.korofin.backend.analysis.dto.RecommendationResponse;
import com.korofin.backend.analysis.service.FinancialAnalysisService;
import com.korofin.backend.analysis.service.MonthEndPredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/**
 * Endpoints REST del resumen financiero del usuario actual.
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final FinancialAnalysisService financialAnalysisService;
    private final MonthEndPredictionService monthEndPredictionService;

    public AnalysisController(
            FinancialAnalysisService financialAnalysisService,
            MonthEndPredictionService monthEndPredictionService
    ) {
        this.financialAnalysisService = financialAnalysisService;
        this.monthEndPredictionService = monthEndPredictionService;
    }

    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryResponse> getSummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        YearMonth period = resolvePeriod(year, month);
        return ResponseEntity.ok(financialAnalysisService.getSummary(period));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations() {
        return ResponseEntity.ok(financialAnalysisService.getRecommendations());
    }

    @GetMapping("/prediction")
    public ResponseEntity<MonthEndPredictionResponse> getPrediction() {
        return ResponseEntity.ok(monthEndPredictionService.predict());
    }

    /** El mes en curso cuando {@code year}/{@code month} están ausentes, el pedido explícito si no. */
    private static YearMonth resolvePeriod(Integer year, Integer month) {
        if (year == null || month == null) {
            return YearMonth.now();
        }
        return YearMonth.of(year, month);
    }
}
