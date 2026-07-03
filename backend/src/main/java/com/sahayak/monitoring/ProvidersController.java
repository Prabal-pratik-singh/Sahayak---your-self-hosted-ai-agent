package com.sahayak.monitoring;

import com.sahayak.agent.AiModelRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Health of every configured AI provider, for the status panel in the topbar. */
@RestController
@RequestMapping("/api")
public class ProvidersController {

    public record ProviderHealth(String id, String label, String model, String status,
                                 long totalRequests, long totalFailures, int consecutiveFailures,
                                 ProviderHealthService.ErrorInfo lastError, String lastSuccessAt) {
    }

    private final AiModelRegistry registry;
    private final ProviderHealthService health;

    public ProvidersController(AiModelRegistry registry, ProviderHealthService health) {
        this.registry = registry;
        this.health = health;
    }

    @GetMapping("/providers/health")
    public List<ProviderHealth> providerHealth() {
        Map<String, ProviderHealthService.HealthSnapshot> snapshots = health.snapshot();
        return registry.options().stream()
                .map(option -> {
                    ProviderHealthService.HealthSnapshot s = snapshots.get(option.id());
                    if (s == null) {
                        return new ProviderHealth(option.id(), option.label(), option.model(),
                                "ok", 0, 0, 0, null, null);
                    }
                    return new ProviderHealth(option.id(), option.label(), option.model(),
                            status(s), s.totalRequests(), s.totalFailures(), s.consecutiveFailures(),
                            s.lastError(), s.lastSuccessAt());
                })
                .toList();
    }

    /** ok = last call worked; limited = quota; error = bad key; down = unreachable/failing. */
    private static String status(ProviderHealthService.HealthSnapshot s) {
        if (s.consecutiveFailures() == 0) {
            return "ok";
        }
        // an old failure with nothing after it fades back to ok after 10 minutes
        if (s.lastError() != null && LocalDateTime.parse(s.lastError().at())
                .isBefore(LocalDateTime.now().minus(Duration.ofMinutes(10)))) {
            return "ok";
        }
        return switch (s.lastError() != null ? s.lastError().category() : "") {
            case "QUOTA" -> "limited";
            case "INVALID_KEY" -> "error";
            default -> "down";
        };
    }
}
