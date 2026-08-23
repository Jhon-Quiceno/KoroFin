package com.korofin.backend.analysis.dto;

/**
 * Una recomendación financiera en texto libre (español), generada por
 * {@code FinancialAnalysisService#getRecommendations} a partir de heurísticas simples sobre el
 * resumen del mes en curso (tasa de ahorro, concentración de gasto por categoría) — sin IA, misma
 * decisión de diseño que {@code TelegramIntentDetector} (dominio {@code integration}): reglas
 * baratas y deterministas en vez de pagar una llamada de IA para un caso que no la necesita.
 *
 * @param title   título corto de la recomendación
 * @param message texto completo, orientado al usuario
 */
public record RecommendationResponse(
        String title,
        String message
) {
}
