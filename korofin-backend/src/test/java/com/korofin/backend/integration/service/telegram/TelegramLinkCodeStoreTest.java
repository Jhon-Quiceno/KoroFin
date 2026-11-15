package com.korofin.backend.integration.service.telegram;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** Store cuyo sorteo de códigos está guionado, para forzar colisiones de forma determinista. */
    private static TelegramLinkCodeStore storeDrawing(Clock clock, String... draws) {
        Deque<String> queue = new ArrayDeque<>(java.util.List.of(draws));
        return new TelegramLinkCodeStore(clock, 300, () -> {
            String next = queue.pollFirst();
            return next != null ? next : draws[draws.length - 1];
        });
    }

    @Test
    void aNewCodeNeverReplacesTheLiveCodeOfAnotherUser() {
        TelegramLinkCodeStore store = storeDrawing(Clock.systemUTC(), "111111", "111111", "222222");
        String victimCode = store.generate(1L);

        String attackerCode = store.generate(2L);

        assertThat(attackerCode).isEqualTo("222222");
        assertThat(store.consume(victimCode)).contains(1L);
    }

    @Test
    void generatingGivesUpInsteadOfReplacingWhenEveryDrawCollides() {
        TelegramLinkCodeStore store = storeDrawing(Clock.systemUTC(), "111111");
        store.generate(1L);

        assertThatThrownBy(() -> store.generate(2L)).isInstanceOf(IllegalStateException.class);
        assertThat(store.consume("111111")).contains(1L);
    }

    @Test
    void generatingANewCodeInvalidatesThePreviousCodeOfTheSameUser() {
        TelegramLinkCodeStore store = storeDrawing(Clock.systemUTC(), "111111", "222222");
        String first = store.generate(1L);

        String second = store.generate(1L);

        assertThat(store.consume(first)).isEmpty();
        assertThat(store.consume(second)).contains(1L);
    }

    @Test
    void anExpiredCodeDoesNotBlockIssuingTheSameValueToSomeoneElse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TelegramLinkCodeStore store = storeDrawing(clock, "111111", "111111");
        store.generate(1L);
        clock.advance(301);

        String code = store.generate(2L);

        assertThat(code).isEqualTo("111111");
        assertThat(store.consume(code)).contains(2L);
    }
}
