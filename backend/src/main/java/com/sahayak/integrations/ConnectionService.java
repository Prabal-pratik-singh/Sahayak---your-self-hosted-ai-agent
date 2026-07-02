package com.sahayak.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahayak.common.CryptoService;
import com.sahayak.integrations.email.EmailSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores and reads per-user connections. Credentials go through
 * {@link CryptoService} on the way in and out of the database.
 */
@Service
public class ConnectionService {

    /** The decrypted LinkedIn credentials for one user. */
    public record LinkedInAccount(String accessToken, String personSub, String displayName, LocalDateTime expiresAt) {
        public boolean expired() {
            return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
        }
    }

    private record LinkedInConfig(String accessToken, String personSub) {
    }

    private final ConnectionRepository repository;
    private final CryptoService crypto;
    private final ObjectMapper objectMapper;

    public ConnectionService(ConnectionRepository repository, CryptoService crypto, ObjectMapper objectMapper) {
        this.repository = repository;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
    }

    public List<ConnectionInfo> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ConnectionInfo::from)
                .toList();
    }

    public void delete(Long userId, Long connectionId) {
        Connection connection = repository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such connection."));
        repository.delete(connection);
    }

    public ConnectionInfo saveEmail(Long userId, EmailSettings settings) {
        Connection connection = upsert(userId, Connection.Type.EMAIL);
        connection.setDisplayName(settings.effectiveFrom());
        connection.setEncryptedConfig(crypto.encrypt(toJson(settings)));
        connection.setExpiresAt(null);
        return ConnectionInfo.from(repository.save(connection));
    }

    public ConnectionInfo saveLinkedIn(Long userId, String accessToken, String personSub,
                                       String displayName, LocalDateTime expiresAt) {
        Connection connection = upsert(userId, Connection.Type.LINKEDIN);
        connection.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : "LinkedIn account");
        connection.setEncryptedConfig(crypto.encrypt(toJson(new LinkedInConfig(accessToken, personSub))));
        connection.setExpiresAt(expiresAt);
        return ConnectionInfo.from(repository.save(connection));
    }

    public Optional<EmailSettings> emailSettings(Long userId) {
        return repository.findByUserIdAndType(userId, Connection.Type.EMAIL)
                .map(c -> fromJson(crypto.decrypt(c.getEncryptedConfig()), EmailSettings.class));
    }

    public Optional<LinkedInAccount> linkedInAccount(Long userId) {
        return repository.findByUserIdAndType(userId, Connection.Type.LINKEDIN)
                .map(c -> {
                    LinkedInConfig config = fromJson(crypto.decrypt(c.getEncryptedConfig()), LinkedInConfig.class);
                    return new LinkedInAccount(config.accessToken(), config.personSub(),
                            c.getDisplayName(), c.getExpiresAt());
                });
    }

    /** One-line-per-app summary injected into the system prompt so the model knows what it can really do. */
    public String promptSummary(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append(emailSettings(userId)
                .map(s -> "- Email sending: CONNECTED (sends from " + s.effectiveFrom() + ").")
                .orElse("- Email sending: NOT connected. The user can add it in the Connections panel (top-right of the app)."));
        sb.append('\n');
        sb.append(linkedInAccount(userId)
                .map(a -> a.expired()
                        ? "- LinkedIn posting: connection EXPIRED. The user must reconnect LinkedIn in the Connections panel."
                        : "- LinkedIn posting: CONNECTED (posts as " + a.displayName() + ").")
                .orElse("- LinkedIn posting: NOT connected. The user can add it in the Connections panel (top-right of the app)."));
        return sb.toString();
    }

    private Connection upsert(Long userId, Connection.Type type) {
        Connection connection = repository.findByUserIdAndType(userId, type).orElseGet(Connection::new);
        connection.setUserId(userId);
        connection.setType(type);
        return connection;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize connection config", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read stored connection config", e);
        }
    }
}
