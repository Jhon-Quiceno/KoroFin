package com.korofin.backend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    void mailTaskExecutorIsANamedDedicatedPool() {
        Executor executor = config.mailTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        assertThat(((ThreadPoolTaskExecutor) executor).getThreadNamePrefix()).isEqualTo("mail-notif-");
    }

    @Test
    void generalTaskExecutorIsANamedDedicatedPool() {
        Executor executor = config.generalTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        assertThat(((ThreadPoolTaskExecutor) executor).getThreadNamePrefix()).isEqualTo("korofin-async-");
    }
}
