package com.sahayak.keys;

import com.sahayak.agent.AiModelRegistry;
import com.sahayak.agent.ChatClientFactory;
import com.sahayak.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Manage your own AI keys (BYOK) — drives the "AI engine keys" card in Settings. */
@RestController
@RequestMapping("/api/keys")
@Validated
public class KeysController {

    public record KeyInfo(String provider, String label, String model, boolean hasKey,
                          boolean serverAvailable, boolean toolCapable) {
    }

    public record SaveKeyRequest(@NotBlank @Size(max = 300) String apiKey) {
    }

    private final UserAiKeyService keys;
    private final ChatClientFactory factory;
    private final AiModelRegistry registry;

    public KeysController(UserAiKeyService keys, ChatClientFactory factory, AiModelRegistry registry) {
        this.keys = keys;
        this.factory = factory;
        this.registry = registry;
    }

    @GetMapping
    public List<KeyInfo> list(Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        Set<String> mine = keys.providersOf(userId);
        return ChatClientFactory.KNOWN_IDS.stream()
                .map(id -> new KeyInfo(id, factory.labelOf(id), factory.modelOf(id),
                        mine.contains(id), registry.has(id), factory.toolCapable(id)))
                .toList();
    }

    @PutMapping("/{provider}")
    public UserAiKeyService.SaveResult save(@PathVariable String provider,
                                            @Validated @RequestBody SaveKeyRequest request,
                                            Authentication auth) {
        return keys.save(AuthenticatedUser.from(auth).id(), provider.toLowerCase(), request.apiKey());
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> delete(@PathVariable String provider, Authentication auth) {
        keys.delete(AuthenticatedUser.from(auth).id(), provider.toLowerCase());
        return ResponseEntity.noContent().build();
    }
}
