package com.korofin.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Habilita la ejecución de métodos {@code @Async}. El primer consumidor real de
 * {@link #MAIL_EXECUTOR} llega con el dominio {@code notification} (fase posterior —
 * {@code EmailNotificationSender}, para que un SMTP lento nunca bloquee el hilo del request);
 * {@link #GENERAL_EXECUTOR} queda como pool genérico de propósito general para cualquier otra
 * tarea async que no justifique su propio pool dedicado.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Nombre del bean {@link #mailTaskExecutor()}, referenciado vía {@code @Async("mailTaskExecutor")}. */
    public static final String MAIL_EXECUTOR = "mailTaskExecutor";

    /** Nombre del bean {@link #generalTaskExecutor()}, referenciado vía {@code @Async("generalTaskExecutor")}. */
    public static final String GENERAL_EXECUTOR = "generalTaskExecutor";

    @Bean(name = MAIL_EXECUTOR)
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-notif-");
        executor.initialize();
        return executor;
    }

    @Bean(name = GENERAL_EXECUTOR)
    public Executor generalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("korofin-async-");
        executor.initialize();
        return executor;
    }
}
