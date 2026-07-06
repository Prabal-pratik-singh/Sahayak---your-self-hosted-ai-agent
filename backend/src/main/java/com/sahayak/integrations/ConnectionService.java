package com.sahayak.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahayak.common.CryptoService;
import com.sahayak.integrations.email.EmailSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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

    /** A user's own Telegram bot + the chat it writes to. */
    public record TelegramConfig(String botToken, String chatId) {
    }

    /** A single secret webhook URL (Discord / Slack incoming webhooks). */
    public record WebhookConfig(String url) {
    }

    /** The decrypted GitHub credentials for one user. */
    public record GitHubAccount(String accessToken, String displayName) {
    }

    private record GitHubConfig(String accessToken) {
    }

    /**
     * The decrypted Google Calendar credentials for one user. Google access
     * tokens last ~1h; the refresh token mints new ones, so the connection
     * itself never expires from the user's point of view.
     */
    public record GoogleCalendarAccount(String accessToken, String refreshToken, long accessExpiresAtEpoch,
                                        String displayName) {
        public boolean accessExpired() {
            return Instant.now().getEpochSecond() >= accessExpiresAtEpoch - 60;
        }
    }

    private record GoogleCalendarConfig(String accessToken, String refreshToken, long accessExpiresAtEpoch) {
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

    public ConnectionInfo saveTelegram(Long userId, TelegramConfig config, String botUsername) {
        Connection connection = upsert(userId, Connection.Type.TELEGRAM);
        connection.setDisplayName("@" + botUsername + " → chat " + config.chatId());
        connection.setEncryptedConfig(crypto.encrypt(toJson(config)));
        connection.setExpiresAt(null);
        return ConnectionInfo.from(repository.save(connection));
    }

    public Optional<TelegramConfig> telegramConfig(Long userId) {
        return repository.findByUserIdAndType(userId, Connection.Type.TELEGRAM)
                .map(c -> fromJson(crypto.decrypt(c.getEncryptedConfig()), TelegramConfig.class));
    }

    public ConnectionInfo saveWebhook(Long userId, Connection.Type type, String url, String displayName) {
        Connection connection = upsert(userId, type);
        connection.setDisplayName(displayName);
        connection.setEncryptedConfig(crypto.encrypt(toJson(new WebhookConfig(url))));
        connection.setExpiresAt(null);
        return ConnectionInfo.from(repository.save(connection));
    }

    public Optional<String> webhookUrl(Long userId, Connection.Type type) {
        return repository.findByUserIdAndType(userId, type)
                .map(c -> fromJson(crypto.decrypt(c.getEncryptedConfig()), WebhookConfig.class).url());
    }

    public ConnectionInfo saveGitHub(Long userId, String accessToken, String displayName) {
        Connection connection = upsert(userId, Connection.Type.GITHUB);
        connection.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : "GitHub account");
        connection.setEncryptedConfig(crypto.encrypt(toJson(new GitHubConfig(accessToken))));
        connection.setExpiresAt(null); // GitHub OAuth-app tokens don't expire
        return ConnectionInfo.from(repository.save(connection));
    }

    public Optional<GitHubAccount> gitHubAccount(Long userId) {
        return repository.findByUserIdAndType(userId, Connection.Type.GITHUB)
                .map(c -> new GitHubAccount(
                        fromJson(crypto.decrypt(c.getEncryptedConfig()), GitHubConfig.class).accessToken(),
                        c.getDisplayName()));
    }

    public ConnectionInfo saveGoogleCalendar(Long userId, String accessToken, String refreshToken,
                                             long accessExpiresAtEpoch, String displayName) {
        Connection connection = upsert(userId, Connection.Type.GOOGLE_CALENDAR);
        connection.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : "Google Calendar");
        connection.setEncryptedConfig(crypto.encrypt(toJson(
                new GoogleCalendarConfig(accessToken, refreshToken, accessExpiresAtEpoch))));
        // Access tokens auto-refresh, so the connection never "expires" in the UI.
        connection.setExpiresAt(null);
        return ConnectionInfo.from(repository.save(connection));
    }

    public Optional<GoogleCalendarAccount> googleCalendarAccount(Long userId) {
        return repository.findByUserIdAndType(userId, Connection.Type.GOOGLE_CALENDAR)
                .map(c -> {
                    GoogleCalendarConfig config =
                            fromJson(crypto.decrypt(c.getEncryptedConfig()), GoogleCalendarConfig.class);
                    return new GoogleCalendarAccount(config.accessToken(), config.refreshToken(),
                            config.accessExpiresAtEpoch(), c.getDisplayName());
                });
    }

    /** One-line-per-app summary injected into the system prompt so the model knows what it can really do. */
    public String promptSummary(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append(emailSettings(userId)
                .map(s -> "- Email sending: CONNECTED (sends from " + s.effectiveFrom() + ").")
                .orElse("- Email sending: NOT connected."));
        sb.append('\n');
        sb.append(linkedInAccount(userId)
                .map(a -> a.expired()
                        ? "- LinkedIn posting: connection EXPIRED. The user must reconnect LinkedIn on the Integrations page."
                        : "- LinkedIn posting: CONNECTED (posts as " + a.displayName() + ").")
                .orElse("- LinkedIn posting: NOT connected."));
        sb.append('\n');
        sb.append(telegramConfig(userId).isPresent()
                ? "- Telegram: CONNECTED (sendTelegramMessage works)."
                : "- Telegram: NOT connected.");
        sb.append('\n');
        sb.append(webhookUrl(userId, Connection.Type.DISCORD).isPresent()
                ? "- Discord: CONNECTED (sendDiscordMessage works)."
                : "- Discord: NOT connected.");
        sb.append('\n');
        sb.append(webhookUrl(userId, Connection.Type.SLACK).isPresent()
                ? "- Slack: CONNECTED (sendSlackMessage works)."
                : "- Slack: NOT connected.");
        sb.append('\n');
        sb.append(gitHubAccount(userId)
                .map(a -> "- GitHub: CONNECTED as " + a.displayName()
                        + " (createGitHubIssue, listMyGitHubRepos, searchGitHubIssues work).")
                .orElse("- GitHub: NOT connected."));
        sb.append('\n');
        sb.append(googleCalendarAccount(userId)
                .map(a -> "- Google Calendar: CONNECTED as " + a.displayName()
                        + " (createCalendarEvent, listUpcomingCalendarEvents work).")
                .orElse("- Google Calendar: NOT connected."));
        sb.append("\nFor anything NOT connected, the user can set it up on the Integrations page.");
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
