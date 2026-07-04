package com.sahayak.keys;

import com.sahayak.agent.ChatClientFactory;
import com.sahayak.common.CryptoService;
import com.sahayak.monitoring.AiErrorCategory;
import com.sahayak.monitoring.AiErrorClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BYOK — each user can bring their own API key per AI provider. Keys are
 * AES-encrypted at rest; clients are built on demand and cached by
 * (provider + key fingerprint), so one user's key never leaks into another
 * user's requests.
 */
@Service
public class UserAiKeyService {

    private static final Logger log = LoggerFactory.getLogger(UserAiKeyService.class);
    private static final int CLIENT_CACHE_CAP = 256;

    public record SaveResult(String status, String warning) {
    }

    private final UserAiKeyRepository repository;
    private final CryptoService crypto;
    private final ChatClientFactory factory;
    private final ConcurrentHashMap<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    public UserAiKeyService(UserAiKeyRepository repository, CryptoService crypto, ChatClientFactory factory) {
        this.repository = repository;
        this.crypto = crypto;
        this.factory = factory;
    }

    public boolean hasKey(Long userId, String providerId) {
        return repository.existsByUserIdAndProvider(userId, providerId);
    }

    public Set<String> providersOf(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(UserAiKey::getProvider)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** The user's own client for this provider, if they saved a key for it. */
    public Optional<ChatClient> clientFor(Long userId, String providerId) {
        return repository.findByUserIdAndProvider(userId, providerId)
                .map(stored -> {
                    String apiKey = crypto.decrypt(stored.getEncryptedKey());
                    if (clientCache.size() > CLIENT_CACHE_CAP) {
                        clientCache.clear(); // crude but safe bound; clients rebuild on demand
                    }
                    return clientCache.computeIfAbsent(providerId + ":" + fingerprint(apiKey),
                            k -> factory.create(providerId, apiKey));
                });
    }

    /**
     * Verifies the key with a tiny real request (retried once, since first
     * calls sometimes hit cold-start flakes), then stores it encrypted.
     * A key the provider outright rejects is refused; a key that is merely
     * rate-limited right now is saved with an honest warning.
     */
    public SaveResult save(Long userId, String providerId, String apiKey) {
        requireKnown(providerId);
        String trimmed = apiKey.strip();
        String label = factory.labelOf(providerId);

        String warning = null;
        AiErrorCategory category = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                factory.createBare(providerId, trimmed)
                        .prompt().user("Reply with exactly: OK").call().content();
                category = null;
                break;
            } catch (Exception e) {
                category = AiErrorClassifier.classify(e);
                log.warn("Key verification for {} failed (attempt {}): [{}] {}: {}",
                        providerId, attempt, category, e.getClass().getSimpleName(), e.getMessage());
                if (category == AiErrorCategory.INVALID_KEY || category == AiErrorCategory.QUOTA) {
                    break; // definitive answers — retrying won't change them
                }
            }
        }
        if (category == AiErrorCategory.INVALID_KEY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " rejected that key — check for typos or create a fresh one.");
        }
        if (category != null) {
            warning = switch (category) {
                case QUOTA -> "Key saved. " + label + " says it is rate-limited right now — it will work once the quota resets.";
                default -> "Key saved, but it could not be verified right now (" + label + " was unreachable). It will be used on your next message.";
            };
        }

        UserAiKey record = repository.findByUserIdAndProvider(userId, providerId).orElseGet(UserAiKey::new);
        record.setUserId(userId);
        record.setProvider(providerId);
        record.setEncryptedKey(crypto.encrypt(trimmed));
        repository.save(record);
        log.info("User {} saved their own {} key{}", userId, providerId, warning == null ? " (verified)" : " (unverified)");
        return new SaveResult("saved", warning);
    }

    public void delete(Long userId, String providerId) {
        requireKnown(providerId);
        repository.findByUserIdAndProvider(userId, providerId).ifPresent(repository::delete);
    }

    private void requireKnown(String providerId) {
        if (!factory.known(providerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown AI provider '" + providerId + "'.");
        }
    }

    private static String fingerprint(String apiKey) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
