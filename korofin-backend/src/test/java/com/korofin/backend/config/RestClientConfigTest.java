package com.korofin.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    @Test
    void restClientBuilderProducesAUsableBuilder() {
        RestClientConfig config = new RestClientConfig(60L);

        RestClient.Builder builder = config.restClientBuilder();

        assertThat(builder).isNotNull();
        assertThat(builder.clone().build()).isNotNull();
    }
}
