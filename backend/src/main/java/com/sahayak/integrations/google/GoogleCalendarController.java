package com.sahayak.integrations.google;

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
 * The two halves of the Google OAuth dance — same shape as LinkedIn/GitHub:
 * /authorize hands the browser a Google consent URL, /callback receives the
 * code, stores the connection (access + refresh token), and sends the browser
 * back to the app.
 */
@RestController
@RequestMapping("/api/integrations/google-calendar")
public class GoogleCalendarController {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarController.class);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

    private record PendingState(Long userId, Instant createdAt) {
    }

    private final ConcurrentHashMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private final GoogleCalendarService googleCalendarService;
    private final ConnectionService connectionService;
    private final String frontendUrl;

    public GoogleCalendarController(GoogleCalendarService googleCalendarService,
                                    ConnectionService connectionService,
                                    @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.googleCalendarService = googleCalendarService;
        this.connectionService = connectionService;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/authorize")
    public Map<String, String> authorize(Authentication authentication) {
        if (!googleCalendarService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Google Calendar is not configured on this server. The server owner must set "
                            + "GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET (see the README).");
        }
        purgeOldStates();
        var user = AuthenticatedUser.from(authentication);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingStates.put(state, new PendingState(user.id(), Instant.now()));
        return Map.of("url", googleCalendarService.authorizeUrl(state));
    }

    /** Google redirects the browser here; public, but only accepts states we issued. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        PendingState pending = state != null ? pendingStates.remove(state) : null;
        boolean stateValid = pending != null
                && pending.createdAt().isAfter(Instant.now().minus(STATE_LIFETIME));

        if (error != null || code == null || !stateValid) {
            return redirectToApp("error", "Google Calendar connection was cancelled or timed out. Try again.");
        }
        try {
            GoogleCalendarService.Token token = googleCalendarService.exchangeCode(code);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                return redirectToApp("error", "Google did not return an access token. Try again.");
            }
            if (token.refreshToken() == null || token.refreshToken().isBlank()) {
                // Should not happen (we request prompt=consent), but without a
                // refresh token the connection would die within the hour.
                return redirectToApp("error", "Google did not return a refresh token. "
                        + "Remove Sahayak at myaccount.google.com/permissions and connect again.");
            }
            String label = "Google Calendar";
            try {
                GoogleCalendarService.UserInfo info = googleCalendarService.userInfo(token.accessToken());
                if (info != null && info.email() != null && !info.email().isBlank()) {
                    label = info.email();
                }
            } catch (Exception e) {
                log.warn("Could not read Google account email, using generic label: {}", e.getMessage());
            }
            long expiresAtEpoch = Instant.now().getEpochSecond() + Math.max(60, token.expiresInSeconds());
            connectionService.saveGoogleCalendar(pending.userId(),
                    token.accessToken(), token.refreshToken(), expiresAtEpoch, label);
            return redirectToApp("connected", "google-calendar");
        } catch (Exception e) {
            log.error("Google Calendar connection failed", e);
            return redirectToApp("error", "Google Calendar connection failed. Check the server logs.");
        }
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
