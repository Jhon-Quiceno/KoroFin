package com.korofin.backend.integration.service.telegram;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramLinkCodeStoreTest {

    @Test
    void generatesASixDigitNumericCode() {
        TelegramLinkCodeStore store = new TelegramLinkCodeStore(Clock.systemUTC(), 300);

        String code = store.generate(1L);

        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
    }

    @Test
    void consumeReturnsTheAssociatedUserIdForAValidCode() {
        TelegramLinkCodeStore store = new TelegramLinkCodeStore(Clock.systemUTC(), 300);
        String code = store.generate(42L);

        Optional<Long> userId = store.consume(code);

        assertThat(userId).contains(42L);
    }

    @Test
    void consumeReturnsEmptyForAnUnknownCode() {
        TelegramLinkCodeStore store = new TelegramLinkCodeStore(Clock.systemUTC(), 300);

        assertThat(store.consume("000000")).isEmpty();
    }

    @Test
    void codeCanOnlyBeConsumedOnce() {
        TelegramLinkCodeStore store = new TelegramLinkCodeStore(Clock.systemUTC(), 300);
        String code = store.generate(1L);

        store.consume(code);
        Optional<Long> secondAttempt = store.consume(code);

        assertThat(secondAttempt).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnExpiredCode() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        TelegramLinkCodeStore store = new TelegramLinkCodeStore(clock, 60);
        String code = store.generate(1L);

        clock.advance(61);

        assertThat(store.consume(code)).isEmpty();
    }

    /** Reloj mutable simple para simular el paso del tiempo dentro de un test sin sleeps reales. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
