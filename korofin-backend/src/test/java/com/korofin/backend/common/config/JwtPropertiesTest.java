package com.korofin.backend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAppJwtPropertiesFromEnvironment() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=una-clave-secreta-larga-para-pruebas-hmac-sha256",
                        "app.jwt.issuer=korofin-backend",
                        "app.jwt.access-expiration-ms=900000",
                        "app.jwt.refresh-expiration-ms=604800000"
                )
                .run(context -> {
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.secret()).isEqualTo("una-clave-secreta-larga-para-pruebas-hmac-sha256");
                    assertThat(properties.issuer()).isEqualTo("korofin-backend");
                    assertThat(properties.accessExpirationMs()).isEqualTo(900000L);
                    assertThat(properties.refreshExpirationMs()).isEqualTo(604800000L);
                });
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }
}
