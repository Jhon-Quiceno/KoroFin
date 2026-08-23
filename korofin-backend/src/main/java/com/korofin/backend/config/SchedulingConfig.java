package com.korofin.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la ejecución de métodos {@code @Scheduled}. Los jobs viven en
 * {@code service/scheduling/} ({@code PaymentReminderJob}, {@code WeeklySummaryJob},
 * {@code InactivityReminderJob}, {@code CardCycleCloseJob}), condicionados a
 * {@code app.jobs.enabled} (default {@code true}) para que tests/entornos que no necesiten jobs
 * corriendo puedan apagar toda la infraestructura de scheduling con una sola propiedad.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
