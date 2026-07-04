package com.sahayak.agent;

import com.sahayak.keys.UserAiKeyService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides, per user, which AI engines exist and WHOSE key each request uses:
 * the user's own key wins; the server's key is the fallback. Health is
 * tracked per key scope, so one user's exhausted quota never shows up as a
 * problem for anyone else.
 */
@Service
public class ProviderAccessService {

    /** One engine available to this user. */
    public record AccessOption(String id, String label, String model, boolean ownKey, boolean toolCapable) {
    }

    /** A fully resolved choice, ready to run a chat turn. */
    public record ResolvedProvider(String id, String label, ChatClient client, String healthScope,
                                   boolean toolCapable) {
    }

    private final AiModelRegistry registry;
    private final ChatClientFactory factory;
    private final UserAiKeyService userKeys;

    public ProviderAccessService(AiModelRegistry registry, ChatClientFactory factory, UserAiKeyService userKeys) {
        this.registry = registry;
        this.factory = factory;
        this.userKeys = userKeys;
    }

    public List<AccessOption> optionsFor(Long userId) {
        Set<String> mine = userKeys.providersOf(userId);
        List<AccessOption> options = new ArrayList<>();
        for (String id : ChatClientFactory.KNOWN_IDS) {
            boolean own = mine.contains(id);
            if (own || registry.has(id)) {
                options.add(new AccessOption(id, factory.labelOf(id), factory.modelOf(id), own,
                        factory.toolCapable(id)));
            }
        }
        return options;
    }

    /** The engine used when the user doesn't pick one: server default, else their first key. */
    public String defaultFor(Long userId) {
        String serverDefault = registry.defaultId();
        if (serverDefault != null) {
            return serverDefault;
        }
        return optionsFor(userId).stream().map(AccessOption::id).findFirst().orElse(null);
    }

    public ResolvedProvider resolve(Long userId, String requested) {
        String id = (requested == null || requested.isBlank())
                ? defaultFor(userId)
                : requested.trim().toLowerCase();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No AI engine is available yet — add your own API key in Settings → AI engine keys.");
        }
        if (!factory.known(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown AI provider '" + requested + "'.");
        }
        Optional<ChatClient> own = userKeys.clientFor(userId, id);
        if (own.isPresent()) {
            return new ResolvedProvider(id, factory.labelOf(id), own.get(), id + ":u" + userId,
                    factory.toolCapable(id));
        }
        ChatClient server = registry.clientOf(id);
        if (server != null) {
            return new ResolvedProvider(id, factory.labelOf(id), server, id, factory.toolCapable(id));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                factory.labelOf(id) + " is not available to you — this server has no key for it. "
                        + "Add your own key in Settings, or pick another engine.");
    }

    /**
     * For scheduled tasks: tasks exist to DO things, so a chat-only engine is
     * useless for them — prefer the stored engine, then any tool-capable one,
     * then whatever exists (the model will at least explain the limitation).
     */
    public ResolvedProvider resolveForTask(Long userId, String stored) {
        try {
            ResolvedProvider resolved = resolve(userId, stored);
            if (resolved.toolCapable()) {
                return resolved;
            }
        } catch (ResponseStatusException e) {
            /* stored engine gone — fall through */
        }
        return optionsFor(userId).stream()
                .filter(AccessOption::toolCapable)
                .findFirst()
                .map(o -> resolve(userId, o.id()))
                .orElseGet(() -> resolve(userId, null));
    }
}
