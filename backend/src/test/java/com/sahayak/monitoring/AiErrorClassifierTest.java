package com.sahayak.monitoring;

import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiErrorClassifierTest {

    @Test
    void classifiesGeminiQuotaError() {
        // the real shape Gemini free tier returns
        var e = new RuntimeException("Failed to generate content",
                new RuntimeException("429 . You exceeded your current quota, please check your plan"));
        assertEquals(AiErrorCategory.QUOTA, AiErrorClassifier.classify(e));
    }

    @Test
    void classifiesInvalidKeys() {
        assertEquals(AiErrorCategory.INVALID_KEY,
                AiErrorClassifier.classify(new RuntimeException("400 API key not valid. Please pass a valid API key.")));
        assertEquals(AiErrorCategory.INVALID_KEY,
                AiErrorClassifier.classify(new RuntimeException("401 Unauthorized - incorrect API key provided")));
        assertEquals(AiErrorCategory.INVALID_KEY,
                AiErrorClassifier.classify(new RuntimeException("PERMISSION_DENIED: permission denied for model")));
    }

    @Test
    void classifiesProviderDowntime() {
        assertEquals(AiErrorCategory.PROVIDER_DOWN,
                AiErrorClassifier.classify(new RuntimeException("503 Service Unavailable")));
        assertEquals(AiErrorCategory.PROVIDER_DOWN,
                AiErrorClassifier.classify(new RuntimeException("The model is overloaded. Please try again later.")));
    }

    @Test
    void classifiesNetworkAndTimeout() {
        assertEquals(AiErrorCategory.NETWORK,
                AiErrorClassifier.classify(new RuntimeException("boom", new UnknownHostException("api.example.com"))));
        assertEquals(AiErrorCategory.TIMEOUT, AiErrorClassifier.classify(new TimeoutException()));
        assertEquals(AiErrorCategory.UNKNOWN, AiErrorClassifier.classify(new RuntimeException("weird")));
    }

    @Test
    void friendlyMessagesNameTheProvider() {
        String msg = AiErrorClassifier.friendly(AiErrorCategory.QUOTA, "Gemini");
        assertTrue(msg.startsWith("Gemini"), msg);
        assertTrue(msg.toLowerCase().contains("quota") || msg.toLowerCase().contains("rate limit"), msg);
    }

    @Test
    void providerReasonIsTrimmedAndSingleLine() {
        var e = new RuntimeException("outer", new RuntimeException("inner\nline   two   " + "x".repeat(300)));
        String reason = AiErrorClassifier.providerReason(e);
        assertTrue(reason.length() <= 181, reason);
        assertTrue(reason.startsWith("inner line two"), reason);
    }
}
