package com.sahayak.integrations.composio;

import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.integrations.ConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Composio-hosted Gmail connect flow — same two-step shape as our direct
 * OAuth integrations: /authorize returns the URL to visit (Composio's hosted
 * consent, using THEIR verified Google app), /callback receives the browser
 * afterwards, confirms with Composio that the account is really ACTIVE, and
 * stores the connection.
 */
@RestController
@RequestMapping("/api/integrations/composio-gmail")
public class ComposioGmailController {

    private static final Logger log = LoggerFactory.getLogger(ComposioGmailController.class);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

    private record PendingState(Long userId, String connectedAccountId, Instant createdAt) {
    }

    private final ConcurrentHashMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private final ComposioService composioService;
    private final ConnectionService connectionService;
    private final String baseUrl;
    private final String frontendUrl;

    public ComposioGmailController(ComposioService composioService,
                                   ConnectionService connectionService,
                                   @Value("${app.base-url:http://localhost:8080}") String baseUrl,
                                   @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.composioService = composioService;
        this.connectionService = connectionService;
        this.baseUrl = baseUrl;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/authorize")
    public Map<String, String> authorize(Authentication authentication) {
        if (!composioService.gmailConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "One-click Gmail is not enabled on this server. The server owner must set "
                            + "COMPOSIO_API_KEY and COMPOSIO_GMAIL_AUTH_CONFIG_ID (see the README). "
                            + "You can always connect email directly with an app password above.");
        }
        purgeOldStates();
        var user = AuthenticatedUser.from(authentication);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String callback = baseUrl + "/api/integrations/composio-gmail/callback?state=" + state;
        ComposioService.LinkSession session =
                composioService.createGmailLink(userTag(user.id()), callback);
        pendingStates.put(state, new PendingState(user.id(), session.connectedAccountId(), Instant.now()));
        return Map.of("url", session.redirectUrl());
    }

    /** Composio sends the browser back here; public, but only accepts states we issued. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state) {
        PendingState pending = state != null ? pendingStates.remove(state) : null;
        boolean stateValid = pending != null
                && pending.createdAt().isAfter(Instant.now().minus(STATE_LIFETIME));
        if (!stateValid) {
            return redirectToApp("error", "The Gmail connection attempt expired. Try again.");
        }
        try {
            // Don't trust the redirect alone — ask Composio whether the account
            // really became ACTIVE (it can lag a moment behind the redirect).
            String status = "";
            for (int attempt = 0; attempt < 5; attempt++) {
                status = composioService.accountStatus(pending.connectedAccountId());
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    break;
                }
                Thread.sleep(1000);
            }
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                log.warn("Composio Gmail account {} ended in status '{}'", pending.connectedAccountId(), status);
                return redirectToApp("error",
                        "Google sign-in did not complete (status: " + status + "). Try again.");
            }
            connectionService.saveComposioGmail(pending.userId(), pending.connectedAccountId(),
                    userTag(pending.userId()), "Gmail via Composio");
            return redirectToApp("connected", "gmail-composio");
        } catch (Exception e) {
            log.error("Composio Gmail connection failed", e);
            return redirectToApp("error", "Gmail connection via Composio failed. Check the server logs.");
        }
    }

    /** Stable per-user id on Composio's side, so reconnects reuse the same entity. */
    private static String userTag(Long userId) {
        return "sahayak-u" + userId;
    }

    private ResponseEntity<Void> redirectToApp(String param, String value) {
        String url = frontendUrl + "/?" + param + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private void purgeOldStates() {
        Instant cutoff = Instant.now().minus(STATE_LIFETIME);
        pendingStates.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
