package com.sahayak.agent;

import com.sahayak.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The AI brains available to THIS user — the server's configured providers
 * plus any the user unlocked with their own key (BYOK).
 */
@RestController
@RequestMapping("/api")
public class ModelsController {

    public record ModelOption(String id, String label, String model, String source, boolean tools) {
    }

    public record ModelsResponse(String defaultId, List<ModelOption> options) {
    }

    private final ProviderAccessService access;

    public ModelsController(ProviderAccessService access) {
        this.access = access;
    }

    @GetMapping("/models")
    public ModelsResponse models(Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        List<ModelOption> options = access.optionsFor(userId).stream()
                .map(o -> new ModelOption(o.id(), o.label(), o.model(),
                        o.ownKey() ? "your key" : "server", o.toolCapable()))
                .toList();
        return new ModelsResponse(access.defaultFor(userId), options);
    }
}
