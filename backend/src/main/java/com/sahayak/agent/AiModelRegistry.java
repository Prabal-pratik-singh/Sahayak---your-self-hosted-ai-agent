package com.sahayak.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Holds one ready-to-use ChatClient per configured AI provider
 * ("anthropic", "openai", "gemini"). A provider is configured when its API
 * key is set; the server works with any subset. All clients share the same
 * chat memory, so a conversation keeps its history even if the user switches
 * brains halfway.
 */
public class AiModelRegistry {

    /** What the UI needs to render the model picker. */
    public record Option(String id, String label, String model) {
    }

    public record Provider(Option option, ChatClient client) {
    }

    private final LinkedHashMap<String, Provider> providers;
    private final String defaultId;

    /**
     * @param providers        insertion order defines the fallback default
     * @param preferredDefault provider id from APP_DEFAULT_AI; blank or unknown → first configured
     */
    public AiModelRegistry(LinkedHashMap<String, Provider> providers, String preferredDefault) {
        this.providers = providers;
        this.defaultId = (preferredDefault != null && providers.containsKey(preferredDefault.trim().toLowerCase()))
                ? preferredDefault.trim().toLowerCase()
                : providers.keySet().stream().findFirst().orElse(null);
    }

    public boolean isEmpty() {
        return providers.isEmpty();
    }

    public String defaultId() {
        return defaultId;
    }

    public List<Option> options() {
        return providers.values().stream().map(Provider::option).toList();
    }

    /** For chat requests: null/blank means the default; an unknown id is a user error. */
    public ChatClient forChat(String requestedId) {
        String id = (requestedId == null || requestedId.isBlank()) ? defaultId : requestedId.trim().toLowerCase();
        Provider provider = providers.get(id);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The AI provider '" + requestedId + "' is not configured on this server. Available: "
                            + String.join(", ", providers.keySet()) + ".");
        }
        return provider.client();
    }

    /**
     * For scheduled tasks: the stored provider may have been unconfigured since
     * scheduling — fall back to the default instead of failing the task.
     */
    public ChatClient forTask(String storedId) {
        if (storedId != null && providers.containsKey(storedId)) {
            return providers.get(storedId).client();
        }
        return providers.get(defaultId).client();
    }

    /** Normalizes a requested id to the one that will actually be used (for storing on tasks). */
    public String resolveId(String requestedId) {
        String id = (requestedId == null || requestedId.isBlank()) ? defaultId : requestedId.trim().toLowerCase();
        return providers.containsKey(id) ? id : defaultId;
    }
}
