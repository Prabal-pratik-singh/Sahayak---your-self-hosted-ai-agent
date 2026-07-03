package com.sahayak.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderHealthServiceTest {

    private final ProviderHealthService health = new ProviderHealthService();

    @Test
    void failuresAreCountedClassifiedAndLabeled() {
        ProviderException e = health.failure("gemini", "Gemini",
                new RuntimeException("429 quota exceeded"));

        assertEquals(AiErrorCategory.QUOTA, e.category());
        assertEquals("gemini", e.providerId());
        assertTrue(e.friendlyMessage().startsWith("Gemini"), e.friendlyMessage());

        var snap = health.snapshot().get("gemini");
        assertEquals(1, snap.totalRequests());
        assertEquals(1, snap.totalFailures());
        assertEquals(1, snap.consecutiveFailures());
        assertEquals("QUOTA", snap.lastError().category());
    }

    @Test
    void successResetsTheFailureStreak() {
        health.failure("openai", "ChatGPT", new RuntimeException("503 unavailable"));
        health.failure("openai", "ChatGPT", new RuntimeException("503 unavailable"));
        health.recordSuccess("openai");

        var snap = health.snapshot().get("openai");
        assertEquals(3, snap.totalRequests());
        assertEquals(2, snap.totalFailures());
        assertEquals(0, snap.consecutiveFailures());
        assertNotNull(snap.lastSuccessAt());
    }

    @Test
    void alreadyClassifiedExceptionsPassThroughWithoutDoubleCounting() {
        ProviderException first = health.failure("gemini", "Gemini", new RuntimeException("429 quota"));
        ProviderException second = health.failure("gemini", "Gemini", first);

        assertSame(first, second);
        assertEquals(1, health.snapshot().get("gemini").totalFailures());
    }
}
