package com.korofin.backend.integration.service.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramIntentDetectorTest {

    private final TelegramIntentDetector detector = new TelegramIntentDetector();

    @Test
    void detectsAQuestionMarkAsAQuery() {
        assertThat(detector.looksLikeSummaryQuery("¿cuánto llevo gastado?")).isTrue();
    }

    @Test
    void detectsSpanishQuestionKeywordsAsAQuery() {
        assertThat(detector.looksLikeSummaryQuery("cuanto gaste en comida este mes")).isTrue();
    }

    @Test
    void detectsBalanceKeywordAsAQuery() {
        assertThat(detector.looksLikeSummaryQuery("dame el balance de este mes")).isTrue();
    }

    @Test
    void doesNotTreatARegistrationAttemptAsAQuery() {
        assertThat(detector.looksLikeSummaryQuery("Uber 15000")).isFalse();
    }

    @Test
    void doesNotTreatABlankMessageAsAQuery() {
        assertThat(detector.looksLikeSummaryQuery("   ")).isFalse();
    }
}
