package com.korofin.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provee el {@link Clock} inyectado en toda lógica de negocio sensible a la fecha/hora, para que
 * los tests puedan sustituir un reloj fijo en vez de depender de {@code Instant.now()}/
 * {@code LocalDate.now()} directo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
