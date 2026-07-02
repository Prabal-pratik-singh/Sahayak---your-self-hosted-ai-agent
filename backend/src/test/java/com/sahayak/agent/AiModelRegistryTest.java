package com.sahayak.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiModelRegistryTest {

    private final ChatClient claude = mock(ChatClient.class);
    private final ChatClient chatgpt = mock(ChatClient.class);

    private AiModelRegistry registry(String preferredDefault) {
        var providers = new LinkedHashMap<String, AiModelRegistry.Provider>();
        providers.put("anthropic", new AiModelRegistry.Provider(
                new AiModelRegistry.Option("anthropic", "Claude", "claude-sonnet-5"), claude));
        providers.put("openai", new AiModelRegistry.Provider(
                new AiModelRegistry.Option("openai", "ChatGPT", "gpt-5-mini"), chatgpt));
        return new AiModelRegistry(providers, preferredDefault);
    }

    @Test
    void firstConfiguredProviderIsDefaultWhenNoPreference() {
        assertEquals("anthropic", registry("").defaultId());
    }

    @Test
    void preferredDefaultWinsWhenConfigured() {
        assertEquals("openai", registry("openai").defaultId());
    }

    @Test
    void unknownPreferenceFallsBackToFirst() {
        assertEquals("anthropic", registry("gemini").defaultId());
    }

    @Test
    void blankRequestGetsDefaultClient() {
        assertSame(claude, registry("").forChat(null));
        assertSame(claude, registry("").forChat(""));
    }

    @Test
    void unknownProviderInChatIsAUserError() {
        var e = assertThrows(ResponseStatusException.class, () -> registry("").forChat("gemini"));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void tasksFallBackToDefaultWhenStoredProviderGone() {
        assertSame(chatgpt, registry("").forTask("openai"));
        assertSame(claude, registry("").forTask("gemini"));
        assertSame(claude, registry("").forTask(null));
    }

    @Test
    void resolveIdNormalizes() {
        assertEquals("openai", registry("").resolveId("openai"));
        assertEquals("anthropic", registry("").resolveId(null));
        assertEquals("anthropic", registry("").resolveId("gemini"));
    }

    @Test
    void emptyRegistryReportsEmpty() {
        assertTrue(new AiModelRegistry(new LinkedHashMap<>(), "").isEmpty());
    }
}
