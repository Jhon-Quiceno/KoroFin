package com.korofin.backend.common.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class InMemoryRateLimiterTest {

    @Test
    void tryConsumeShouldAllowUpToMaxRequestsWithinWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new MutableClock(Instant.parse("2026-07-16T10:00:00Z")));

        Assertions.assertTrue(limiter.tryConsume("key", 3, Duration.ofSeconds(60)));
        Assertions.assertTrue(limiter.tryConsume("key", 3, Duration.ofSeconds(60)));
        Assertions.assertTrue(limiter.tryConsume("key", 3, Duration.ofSeconds(60)));
    }

    @Test
    void tryConsumeShouldBlockOnceMaxRequestsReachedWithinWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new MutableClock(Instant.parse("2026-07-16T10:00:00Z")));

        Assertions.assertTrue(limiter.tryConsume("key", 2, Duration.ofSeconds(60)));
        Assertions.assertTrue(limiter.tryConsume("key", 2, Duration.ofSeconds(60)));
        Assertions.assertFalse(limiter.tryConsume("key", 2, Duration.ofSeconds(60)));
    }

    @Test
    void tryConsumeShouldRecoverOnceWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T10:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        Assertions.assertTrue(limiter.tryConsume("key", 1, Duration.ofSeconds(60)));
        Assertions.assertFalse(limiter.tryConsume("key", 1, Duration.ofSeconds(60)));

        clock.advance(Duration.ofSeconds(61));

        Assertions.assertTrue(limiter.tryConsume("key", 1, Duration.ofSeconds(60)));
    }

    @Test
    void tryConsumeShouldTrackDifferentKeysIndependently() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new MutableClock(Instant.parse("2026-07-16T10:00:00Z")));

        Assertions.assertTrue(limiter.tryConsume("key-a", 1, Duration.ofSeconds(60)));
        Assertions.assertFalse(limiter.tryConsume("key-a", 1, Duration.ofSeconds(60)));
        Assertions.assertTrue(limiter.tryConsume("key-b", 1, Duration.ofSeconds(60)));
    }

    /** {@link Clock} controlable para simular el paso del tiempo sin dormir el test. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
