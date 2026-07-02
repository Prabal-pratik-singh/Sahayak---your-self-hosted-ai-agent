package com.sahayak.integrations.linkedin;

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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The two halves of the LinkedIn OAuth dance:
 * /authorize gives the browser a LinkedIn consent URL, and /callback receives
 * LinkedIn's answer, stores the connection, and sends the browser back to the app.
 */
@RestController
@RequestMapping("/api/integrations/linkedin")
public class LinkedInController {

    private static final Logger log = LoggerFactory.getLogger(LinkedInController.class);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

    private record PendingState(Long userId, Instant createdAt) {
    }

    private final ConcurrentHashMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private final LinkedInService linkedInService;
    private final ConnectionService connectionService;
    private final String frontendUrl;

    public LinkedInController(LinkedInService linkedInService,
                              ConnectionService connectionService,
                              @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.linkedInService = linkedInService;
        this.connectionService = connectionService;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/authorize")
    public Map<String, String> authorize(Authentication authentication) {
        if (!linkedInService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "LinkedIn is not configured on this server. The server owner must set "
                            + "LINKEDIN_CLIENT_ID and LINKEDIN_CLIENT_SECRET (see the README).");
        }
        purgeOldStates();
        var user = AuthenticatedUser.from(authentication);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingStates.put(state, new PendingState(user.id(), Instant.now()));
        return Map.of("url", linkedInService.authorizeUrl(state));
    }

    /** LinkedIn redirects the browser here; this endpoint is public but only accepts states we issued. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        PendingState pending = state != null ? pendingStates.remove(state) : null;
        boolean stateValid = pending != null
                && pending.createdAt().isAfter(Instant.now().minus(STATE_LIFETIME));

        if (error != null || code == null || !stateValid) {
            return redirectToApp("error", "LinkedIn connection was cancelled or timed out. Try again.");
        }
        try {
            LinkedInService.Token token = linkedInService.exchangeCode(code);
            LinkedInService.Profile profile = linkedInService.profile(token.accessToken());
            connectionService.saveLinkedIn(
                    pending.userId(),
                    token.accessToken(),
                    profile.sub(),
                    profile.name(),
                    LocalDateTime.now().plusSeconds(token.expiresInSeconds()));
            return redirectToApp("connected", "linkedin");
        } catch (Exception e) {
            log.error("LinkedIn connection failed", e);
            return redirectToApp("error", "LinkedIn connection failed. Check the server logs.");
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
