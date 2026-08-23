package com.korofin.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, ProbeJob.class);

    @Test
    void enablesSchedulingByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ScheduledTaskHolder.class);
            ScheduledTaskHolder registrar = context.getBean(ScheduledTaskHolder.class);
            assertThat(registrar.getScheduledTasks()).isNotEmpty();
        });
    }

    @Test
    void disablesSchedulingWhenAppJobsEnabledIsFalse() {
        contextRunner
                .withPropertyValues("app.jobs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledTaskHolder.class));
    }

    @Configuration
    static class ProbeJob {
        @Scheduled(fixedRate = Long.MAX_VALUE)
        void noop() {
        }
    }
}
