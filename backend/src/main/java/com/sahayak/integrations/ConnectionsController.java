package com.sahayak.integrations;

import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.integrations.email.EmailService;
import com.sahayak.integrations.email.EmailSettings;
import com.sahayak.integrations.messaging.TelegramService;
import com.sahayak.integrations.messaging.WebhookService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
@Validated
public class ConnectionsController {

    public record EmailConnectionRequest(
            @NotBlank @Size(max = 190) String host,
            @Min(1) @Max(65535) int port,
            @NotBlank @Size(max = 190) String username,
            @NotBlank @Size(max = 190) String password,
            @Email @Size(max = 190) String fromAddress) {
    }

    public record TelegramConnectionRequest(
            @NotBlank @Size(max = 100) String botToken,
            @NotBlank @Size(max = 40) String chatId) {
    }

    public record WebhookConnectionRequest(
            @NotBlank @Size(max = 500) String webhookUrl) {
    }

    private final ConnectionService connectionService;
    private final EmailService emailService;
    private final TelegramService telegramService;
    private final WebhookService webhookService;

    public ConnectionsController(ConnectionService connectionService,
                                 EmailService emailService,
                                 TelegramService telegramService,
                                 WebhookService webhookService) {
        this.connectionService = connectionService;
        this.emailService = emailService;
        this.telegramService = telegramService;
        this.webhookService = webhookService;
    }

    @GetMapping
    public List<ConnectionInfo> list(Authentication authentication) {
        return connectionService.list(AuthenticatedUser.from(authentication).id());
    }

    /** Connects (or replaces) the user's outgoing-email account after checking the login really works. */
    @PostMapping("/email")
    public ResponseEntity<ConnectionInfo> connectEmail(@Validated @RequestBody EmailConnectionRequest request,
                                                       Authentication authentication) {
        EmailSettings settings = new EmailSettings(
                request.host().trim(), request.port(),
                request.username().trim(), request.password(),
                request.fromAddress() != null ? request.fromAddress().trim() : null);
        emailService.verify(settings);
        ConnectionInfo saved = connectionService.saveEmail(AuthenticatedUser.from(authentication).id(), settings);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Connects the user's own Telegram bot; a test message proves token + chat id work. */
    @PostMapping("/telegram")
    public ResponseEntity<ConnectionInfo> connectTelegram(@Validated @RequestBody TelegramConnectionRequest request,
                                                          Authentication authentication) {
        String botUsername = telegramService.verify(request.botToken().trim(), request.chatId().trim());
        ConnectionInfo saved = connectionService.saveTelegram(
                AuthenticatedUser.from(authentication).id(),
                new ConnectionService.TelegramConfig(request.botToken().trim(), request.chatId().trim()),
                botUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/discord")
    public ResponseEntity<ConnectionInfo> connectDiscord(@Validated @RequestBody WebhookConnectionRequest request,
                                                         Authentication authentication) {
        String url = request.webhookUrl().trim();
        webhookService.verifyDiscord(url);
        ConnectionInfo saved = connectionService.saveWebhook(
                AuthenticatedUser.from(authentication).id(), Connection.Type.DISCORD, url, "Discord channel");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/slack")
    public ResponseEntity<ConnectionInfo> connectSlack(@Validated @RequestBody WebhookConnectionRequest request,
                                                       Authentication authentication) {
        String url = request.webhookUrl().trim();
        webhookService.verifySlack(url);
        ConnectionInfo saved = connectionService.saveWebhook(
                AuthenticatedUser.from(authentication).id(), Connection.Type.SLACK, url, "Slack channel");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        connectionService.delete(AuthenticatedUser.from(authentication).id(), id);
        return ResponseEntity.noContent().build();
    }
}
