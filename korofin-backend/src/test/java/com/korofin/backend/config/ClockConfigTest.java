package com.korofin.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void clockBeanReturnsUtcClockCloseToNow() {
        Clock clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(Duration.between(clock.instant(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }
}
