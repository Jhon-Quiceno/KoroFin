package com.korofin.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Provee el {@link RestClient.Builder} compartido para llamadas HTTP salientes. El primer
 * consumidor real llega con el dominio {@code ai} (fase posterior — proveedores de IA vía
 * {@code RestClient}), pero el bean queda listo desde ya con los timeouts configurados una sola
 * vez acá, en vez de por-llamada, siguiendo el mismo criterio que FinSmart.
 *
 * <p>El timeout de lectura es externalizable vía {@code app.ai.read-timeout-seconds} (default
 * {@code 60}) para que la fase de IA pueda ajustarlo sin tocar código.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final Duration readTimeout;

    public RestClientConfig(@Value("${app.ai.read-timeout-seconds:60}") long readTimeoutSeconds) {
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(buildRequestFactory());
    }

    private ClientHttpRequestFactory buildRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
