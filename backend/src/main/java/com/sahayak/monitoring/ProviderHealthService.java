package com.sahayak.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Live usage & failure tracking for every AI provider. Each call is recorded;
 * failures are classified so both the chat error and the health panel can say
 * WHICH provider failed and WHY, in plain language. In-memory on purpose —
 * Sahayak runs as a single instance and history resets with the process.
 */
@Service
public class ProviderHealthService {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthService.class);

    public record ErrorInfo(String category, String message, String at) {
    }

    public record HealthSnapshot(long totalRequests, long totalFailures, int consecutiveFailures,
                                 ErrorInfo lastError, String lastSuccessAt) {
    }

    private static final class Stats {
        final LongAdder total = new LongAdder();
        final LongAdder failures = new LongAdder();
        final AtomicInteger consecutive = new AtomicInteger();
        volatile ErrorInfo lastError;
        volatile String lastSuccessAt;
    }

    private final ConcurrentHashMap<String, Stats> byProvider = new ConcurrentHashMap<>();

    public void recordSuccess(String providerId) {
        Stats stats = statsFor(providerId);
        stats.total.increment();
        stats.consecutive.set(0);
        stats.lastSuccessAt = LocalDateTime.now().toString();
    }

    /**
     * Records and classifies a failure; returns the exception to rethrow so
     * callers do {@code throw health.failure(...)}. Already-classified
     * exceptions pass through untouched (no double counting).
     */
    public ProviderException failure(String providerId, String providerLabel, Throwable error) {
        if (error instanceof ProviderException alreadyRecorded) {
            return alreadyRecorded;
        }
        Stats stats = statsFor(providerId);
        stats.total.increment();
        stats.failures.increment();
        int streak = stats.consecutive.incrementAndGet();

        AiErrorCategory category = AiErrorClassifier.classify(error);
        String reason = AiErrorClassifier.providerReason(error);
        String message = AiErrorClassifier.friendly(category, providerLabel);
        if (reason != null && category != AiErrorCategory.TIMEOUT) {
            message += " (Provider said: \"" + reason + "\")";
        }
        stats.lastError = new ErrorInfo(category.name(), message, LocalDateTime.now().toString());

        log.warn("AI provider '{}' failure #{} [{}]: {}", providerId, streak, category,
                reason != null ? reason : String.valueOf(error));
        // providerId may be a per-user health scope like "gemini:u4" — the
        // exception (and API error body) should carry only the clean id.
        String cleanId = providerId.contains(":") ? providerId.substring(0, providerId.indexOf(':')) : providerId;
        return new ProviderException(cleanId, providerLabel, category, message, error);
    }

    public Map<String, HealthSnapshot> snapshot() {
        Map<String, HealthSnapshot> out = new ConcurrentHashMap<>();
        byProvider.forEach((id, s) -> out.put(id, new HealthSnapshot(
                s.total.sum(), s.failures.sum(), s.consecutive.get(), s.lastError, s.lastSuccessAt)));
        return out;
    }

    private Stats statsFor(String providerId) {
        return byProvider.computeIfAbsent(providerId, id -> new Stats());
    }
}
