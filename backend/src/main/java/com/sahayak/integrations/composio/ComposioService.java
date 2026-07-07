package com.sahayak.integrations.composio;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Talks to Composio (composio.dev) — the OPTIONAL middleman path for
 * integrations where direct setup is unreasonable for non-technical users.
 * Today that is exactly one thing: Gmail, whose direct route (app passwords)
 * trips people up, and whose direct OAuth route requires Google's paid
 * verification. Composio's verified Google app makes it one click.
 *
 * Honesty contract (mirrored in the UI): with this path, the user's Gmail
 * token is held by Composio and mails are sent through their servers —
 * unlike every direct integration, where credentials never leave this
 * server. The server owner opts in by setting COMPOSIO_API_KEY.
 */
@Service
public class ComposioService {

    private static final String BASE = "https://backend.composio.dev/api/v3";

    /** What the caller needs to send the user off to authorize. */
    public record LinkSession(String redirectUrl, String connectedAccountId) {
    }

    private final RestClient http;
    private final String apiKey;
    private final String gmailAuthConfigId;

    public ComposioService(RestClient.Builder restClientBuilder,
                           @Value("${app.composio.api-key:}") String apiKey,
                           @Value("${app.composio.gmail-auth-config-id:}") String gmailAuthConfigId) {
        this.http = restClientBuilder.build();
        this.apiKey = apiKey;
        this.gmailAuthConfigId = gmailAuthConfigId;
    }

    public boolean gmailConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && gmailAuthConfigId != null && !gmailAuthConfigId.isBlank();
    }

    /**
     * Starts a hosted auth session for Gmail and returns the URL to send the
     * user's browser to, plus the connected-account id we poll afterwards.
     */
    public LinkSession createGmailLink(String userTag, String callbackUrl) {
        JsonNode response = http.post()
                .uri(BASE + "/connected_accounts/link")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "auth_config_id", gmailAuthConfigId,
                        "user_id", userTag,
                        "callback_url", callbackUrl))
                .retrieve()
                .body(JsonNode.class);

        String redirectUrl = response != null ? response.path("redirect_url").asText("") : "";
        String accountId = response != null ? response.path("connected_account_id").asText("") : "";
        if (redirectUrl.isEmpty() || accountId.isEmpty()) {
            throw new IllegalStateException("Composio did not return an auth link. Response: "
                    + (response != null ? response.toString() : "(empty)"));
        }
        return new LinkSession(redirectUrl, accountId);
    }

    /** The connected account's status, e.g. INITIATED / ACTIVE / FAILED / EXPIRED. */
    public String accountStatus(String connectedAccountId) {
        JsonNode response = http.get()
                .uri(BASE + "/connected_accounts/" + connectedAccountId)
                .header("x-api-key", apiKey)
                .retrieve()
                .body(JsonNode.class);
        return response != null ? response.path("status").asText("") : "";
    }

    /**
     * Sends an email through the user's Composio-connected Gmail. Returns null
     * on success, or a human-readable problem description.
     */
    public String sendGmail(String userTag, String connectedAccountId,
                            String to, String subject, String body) {
        JsonNode response = http.post()
                .uri(BASE + "/tools/execute/GMAIL_SEND_EMAIL")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "user_id", userTag,
                        "connected_account_id", connectedAccountId,
                        "arguments", Map.of(
                                "recipient_email", to,
                                "subject", subject,
                                "body", body)))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return "Composio returned an empty response.";
        }
        if (response.path("successful").asBoolean(false)) {
            return null;
        }
        String error = response.path("error").asText("");
        return error.isEmpty() ? "Composio reported the send as unsuccessful." : error;
    }
}
