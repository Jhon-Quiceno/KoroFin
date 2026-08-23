package com.korofin.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la ejecución de métodos {@code @Scheduled}. Todavía no hay ningún job en esta fase
 * (llegan con los dominios de {@code notification}/{@code recurringpayment}/{@code card} en fases
 * posteriores), pero queda listo desde ya, condicionado a {@code app.jobs.enabled} (default
 * {@code true}) para que tests/entornos que no necesiten jobs corriendo puedan apagar toda la
 * infraestructura de scheduling con una sola propiedad.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
