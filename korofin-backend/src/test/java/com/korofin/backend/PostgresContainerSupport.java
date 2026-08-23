package com.korofin.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Interfaz base para tests que necesitan un Postgres real (vía Testcontainers) en vez de una
 * aproximación H2 — las migraciones Flyway usan sintaxis específica de Postgres (ver
 * docs/backend-plan.md sección 15). El contenedor es un singleton por JVM de test (se levanta una
 * sola vez y Testcontainers lo reutiliza entre clases gracias al Ryuk reaper, sin
 * {@code @Container}/ciclo de vida por clase) para no pagar el costo de arranque en cada test.
 *
 * <p>Requiere Docker Desktop (u otro daemon Docker compatible) corriendo localmente.
 */
@Testcontainers
public interface PostgresContainerSupport {

    PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("korofin_test")
            .withUsername("korofin_test")
            .withPassword("korofin_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
