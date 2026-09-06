package com.korofin.backend.ai.service.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class AiProviderPricingTest {

    private final AiProviderPricing pricing = new AiProviderPricing();

    @Test
    void estimateCostShouldReturnZeroForOpenCodesFreeDefaultModel() {
        BigDecimal cost = pricing.estimateCost("opencode", "deepseek-v4-flash-free", 1000);

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(cost));
    }

    @Test
    void estimateCostShouldReturnZeroForAnyModelFollowingTheFreeSuffixConvention() {
        BigDecimal cost = pricing.estimateCost("openrouter", "nvidia/nemotron-nano-12b-v2-vl:free", 5000);

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(cost));
    }

    @Test
    void estimateCostShouldReturnZeroWhenTokensUsedIsZeroOrNegativeRegardlessOfProvider() {
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(pricing.estimateCost("nvidia", "meta/llama-3.1-70b-instruct", 0)));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(pricing.estimateCost("nvidia", "meta/llama-3.1-70b-instruct", -5)));
    }

    @Test
    void estimateCostShouldReturnAPositiveEstimateForNvidiasPaidModel() {
        BigDecimal cost = pricing.estimateCost("nvidia", "meta/llama-3.1-70b-instruct", 1000);

        Assertions.assertNotNull(cost);
        Assertions.assertTrue(cost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void estimateCostShouldReturnAPositiveEstimateForGroqsPaidModel() {
        BigDecimal cost = pricing.estimateCost("groq", "llama-3.3-70b-versatile", 1000);

        Assertions.assertNotNull(cost);
        Assertions.assertTrue(cost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void estimateCostShouldReturnNullForAnUnrecognizedProvider() {
        BigDecimal cost = pricing.estimateCost("some-other-provider", "some-model", 1000);

        Assertions.assertNull(cost);
    }

    @Test
    void estimateCostShouldReturnNullForOpenCodesNonFreeCustomModel() {
        BigDecimal cost = pricing.estimateCost("opencode", "some-other-paid-model", 1000);

        Assertions.assertNull(cost);
    }

    @Test
    void estimateCostShouldScaleLinearlyWithTokensUsed() {
        BigDecimal costFor1000 = pricing.estimateCost("nvidia", "meta/llama-3.1-70b-instruct", 1000);
        BigDecimal costFor2000 = pricing.estimateCost("nvidia", "meta/llama-3.1-70b-instruct", 2000);

        Assertions.assertEquals(0, costFor1000.multiply(BigDecimal.valueOf(2)).compareTo(costFor2000));
    }

    @Test
    void estimateCostShouldRoundToSixDecimalPlaces() {
        BigDecimal cost = pricing.estimateCost("nvidia", "meta/llama-3.1-70b-instruct", 1);

        Assertions.assertTrue(cost.scale() <= 6);
    }
}
