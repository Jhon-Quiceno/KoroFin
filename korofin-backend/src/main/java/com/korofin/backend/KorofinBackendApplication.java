package com.korofin.backend;

import com.korofin.backend.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
@EnableJpaAuditing
public class KorofinBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(KorofinBackendApplication.class, args);
    }
}
