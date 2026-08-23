package com.korofin.backend.ai.service.provider;

import com.korofin.backend.ai.entity.AiUsageEventType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

class AiProviderRegistryTest {

    @Test
    void enabledInPriorityOrderShouldExcludeProvidersWithBlankOrMissingApiKey() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("", "deepseek-v4-flash-free", null),
                "openrouter", new AiProviderProperties.ProviderCredentials(null, "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(1, enabled.size());
        Assertions.assertEquals("nvidia", enabled.get(0).name());
    }

    @Test
    void enabledInPriorityOrderShouldFallBackToCatalogDefaultModelWhenModelIsBlank() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", null, null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        ResolvedAiProvider nvidia = enabled.stream().filter(p -> p.name().equals("nvidia")).findFirst().orElseThrow();
        ResolvedAiProvider opencode = enabled.stream().filter(p -> p.name().equals("opencode")).findFirst().orElseThrow();
        Assertions.assertEquals(SupportedAiProvider.NVIDIA.defaultModel(), nvidia.model());
        Assertions.assertEquals(SupportedAiProvider.OPENCODE.defaultModel(), opencode.model());
    }

    @Test
    void enabledInPriorityOrderShouldRespectConfiguredPriorityOrder() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(List.of("openrouter", "nvidia"));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(2, enabled.size());
        Assertions.assertEquals("openrouter", enabled.get(0).name());
        Assertions.assertEquals("nvidia", enabled.get(1).name());
    }

    @Test
    void enabledInPriorityOrderShouldStillTryAConfiguredProviderThatIsNotListedInPriority() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(List.of("nvidia"));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "deepseek-v4-flash-free", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(
                List.of("nvidia", "opencode", "openrouter"),
                enabled.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void enabledInPriorityOrderShouldDropUnknownBlankAndDuplicateEntriesFromConfiguredPriority() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(Arrays.asList("nvidia", "unknown-provider", "", null, "NVIDIA", "opencode"));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "deepseek-v4-flash-free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(2, enabled.size());
        Assertions.assertEquals("nvidia", enabled.get(0).name());
        Assertions.assertEquals("opencode", enabled.get(1).name());
    }

    @Test
    void enabledInPriorityOrderShouldFallBackToDefaultOrderWhenPriorityIsUnset() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProviders(Map.of(
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null),
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "deepseek-v4-flash-free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(
                List.of("nvidia", "opencode", "openrouter"),
                enabled.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void enabledInPriorityOrderShouldAppendGroqLastInDefaultOrderWhenConfigured() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "deepseek-v4-flash-free", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null),
                "groq", new AiProviderProperties.ProviderCredentials("sk-groq", "llama-3.3-70b-versatile", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(
                List.of("nvidia", "opencode", "openrouter", "groq"),
                enabled.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void enabledInPriorityOrderShouldFallBackToDefaultOrderWhenPriorityBecomesEmptyAfterFiltering() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(Arrays.asList("unknown-1", "", null, "unknown-2"));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "deepseek-v4-flash-free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder();

        Assertions.assertEquals(
                List.of("nvidia", "opencode"),
                enabled.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void statusShouldNeverExposeApiKeyAndShouldReportConfiguredAndPriorityForEveryCatalogEntry() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(List.of("opencode", "nvidia"));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "opencode", new AiProviderProperties.ProviderCredentials("sk-opencode", "deepseek-v4-flash-free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<AiProviderRegistry.AiProviderStatus> statuses = registry.status();

        Assertions.assertEquals(SupportedAiProvider.values().length, statuses.size());
        Assertions.assertEquals(3, AiProviderRegistry.AiProviderStatus.class.getRecordComponents().length);

        AiProviderRegistry.AiProviderStatus opencode = findStatus(statuses, "opencode");
        Assertions.assertTrue(opencode.configured());
        Assertions.assertEquals(1, opencode.priority());

        AiProviderRegistry.AiProviderStatus nvidia = findStatus(statuses, "nvidia");
        Assertions.assertTrue(nvidia.configured());
        Assertions.assertEquals(2, nvidia.priority());

        AiProviderRegistry.AiProviderStatus openrouter = findStatus(statuses, "openrouter");
        Assertions.assertFalse(openrouter.configured());
        Assertions.assertNull(openrouter.priority());

        AiProviderRegistry.AiProviderStatus groq = findStatus(statuses, "groq");
        Assertions.assertFalse(groq.configured());
        Assertions.assertNull(groq.priority());
    }

    private static AiProviderRegistry.AiProviderStatus findStatus(List<AiProviderRegistry.AiProviderStatus> statuses, String name) {
        return statuses.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void enabledInPriorityOrderWithTaskTypeShouldFallBackToGlobalOrderWhenNoOverrideIsConfigured() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(List.of("openrouter", "nvidia"));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> enabled = registry.enabledInPriorityOrder(AiUsageEventType.CATEGORIZE);

        Assertions.assertEquals(
                List.of("openrouter", "nvidia"),
                enabled.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void enabledInPriorityOrderWithTaskTypeShouldUseTheConfiguredOverrideForThatTaskOnly() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(List.of("nvidia", "openrouter"));
        properties.setTaskPriority(Map.of("insight", List.of("openrouter", "nvidia")));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> insightOrder = registry.enabledInPriorityOrder(AiUsageEventType.INSIGHT);
        List<ResolvedAiProvider> categorizeOrder = registry.enabledInPriorityOrder(AiUsageEventType.CATEGORIZE);

        Assertions.assertEquals(
                List.of("openrouter", "nvidia"),
                insightOrder.stream().map(ResolvedAiProvider::name).toList()
        );
        Assertions.assertEquals(
                List.of("nvidia", "openrouter"),
                categorizeOrder.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void enabledInPriorityOrderWithTaskTypeShouldFallBackToGlobalOrderWhenOverrideBecomesEmptyAfterFiltering() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPriority(List.of("openrouter", "nvidia"));
        properties.setTaskPriority(Map.of("insight", Arrays.asList("unknown-provider", "", null)));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> insightOrder = registry.enabledInPriorityOrder(AiUsageEventType.INSIGHT);

        Assertions.assertEquals(
                List.of("openrouter", "nvidia"),
                insightOrder.stream().map(ResolvedAiProvider::name).toList()
        );
    }

    @Test
    void enabledInPriorityOrderWithTaskTypeShouldStillTryAnEnabledProviderNotListedInTheOverride() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setTaskPriority(Map.of("chat", List.of("openrouter")));
        properties.setProviders(Map.of(
                "nvidia", new AiProviderProperties.ProviderCredentials("sk-nvidia", "meta/llama-3.1-70b-instruct", null),
                "openrouter", new AiProviderProperties.ProviderCredentials("sk-openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", null)
        ));
        AiProviderRegistry registry = new AiProviderRegistry(properties);

        List<ResolvedAiProvider> chatOrder = registry.enabledInPriorityOrder(AiUsageEventType.CHAT);

        Assertions.assertEquals(
                List.of("openrouter", "nvidia"),
                chatOrder.stream().map(ResolvedAiProvider::name).toList()
        );
    }
}
