package com.korofin.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void customOpenApiExposesKorofinTitleAndLocalServer() {
        OpenAPI openApi = new OpenApiConfig().customOpenAPI();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("KoroFin API");
        assertThat(openApi.getServers()).hasSize(1);
    }
}
