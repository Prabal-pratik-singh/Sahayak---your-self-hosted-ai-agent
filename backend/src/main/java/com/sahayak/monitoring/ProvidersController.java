package com.sahayak.monitoring;

import com.sahayak.agent.ProviderAccessService;
import com.sahayak.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Health of every AI engine available to the CURRENT user, measured on the
 * key they would actually use — their own key's stats if they brought one,
 * the server key's otherwise.
 */
@RestController
@RequestMapping("/api")
public class ProvidersController {

    public record ProviderHealth(String id, String label, String model, String source, String status,
                                 long totalRequests, long totalFailures, int consecutiveFailures,
                                 ProviderHealthService.ErrorInfo lastError, String lastSuccessAt) {
    }

    private final ProviderAccessService access;
    private final ProviderHealthService health;

    public ProvidersController(ProviderAccessService access, ProviderHealthService health) {
        this.access = access;
        this.health = health;
    }

    @GetMapping("/providers/health")
    public List<ProviderHealth> providerHealth(Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        Map<String, ProviderHealthService.HealthSnapshot> snapshots = health.snapshot();

        return access.optionsFor(userId).stream()
                .map(option -> {
                    String scope = option.ownKey() ? option.id() + ":u" + userId : option.id();
                    String source = option.ownKey() ? "your key" : "server key";
                    ProviderHealthService.HealthSnapshot s = snapshots.get(scope);
                    if (s == null) {
                        return new ProviderHealth(option.id(), option.label(), option.model(), source,
                                "ok", 0, 0, 0, null, null);
                    }
                    return new ProviderHealth(option.id(), option.label(), option.model(), source,
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
