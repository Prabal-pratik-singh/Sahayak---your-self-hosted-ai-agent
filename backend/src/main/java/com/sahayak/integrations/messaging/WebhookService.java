package com.sahayak.integrations.messaging;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Discord and Slack both offer "incoming webhooks": a secret URL that posts a
 * message into one channel. No app review, free, two-minute setup. Host names
 * are strictly allow-listed so a webhook URL can never be abused to make the
 * server call somewhere else (SSRF protection).
 */
@Service
public class WebhookService {

    private static final Set<String> DISCORD_HOSTS =
            Set.of("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");
    private static final Set<String> SLACK_HOSTS = Set.of("hooks.slack.com");

    private final RestClient http;

    public WebhookService(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public void verifyDiscord(String webhookUrl) {
        requireHost(webhookUrl, DISCORD_HOSTS, "a Discord webhook (https://discord.com/api/webhooks/…)");
        String problem = post(webhookUrl, Map.of("content", "✅ Sahayak connected to this channel."));
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discord rejected that webhook: " + problem);
        }
    }

    public void verifySlack(String webhookUrl) {
        requireHost(webhookUrl, SLACK_HOSTS, "a Slack webhook (https://hooks.slack.com/…)");
        String problem = post(webhookUrl, Map.of("text", "✅ Sahayak connected to this channel."));
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack rejected that webhook: " + problem);
        }
    }

    /** Returns null on success or a human-readable problem. */
    public String sendDiscord(String webhookUrl, String text) {
        if (hostProblem(webhookUrl, DISCORD_HOSTS) != null) {
            return "stored webhook URL is not a Discord webhook";
        }
        return post(webhookUrl, Map.of("content", truncate(text, 1900)));
    }

    /** Returns null on success or a human-readable problem. */
    public String sendSlack(String webhookUrl, String text) {
        if (hostProblem(webhookUrl, SLACK_HOSTS) != null) {
            return "stored webhook URL is not a Slack webhook";
        }
        return post(webhookUrl, Map.of("text", truncate(text, 3000)));
    }

    private String post(String url, Map<String, Object> body) {
        try {
            var response = http.post().uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> true, (req, resp) -> { /* judged below */ })
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            return "HTTP " + response.getStatusCode().value();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static void requireHost(String url, Set<String> allowedHosts, String expected) {
        String problem = hostProblem(url, allowedHosts);
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That does not look like " + expected + ": " + problem);
        }
    }

    private static String hostProblem(String url, Set<String> allowedHosts) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return "must be https";
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            return allowedHosts.contains(host) ? null : "unexpected host " + host;
        } catch (Exception e) {
            return "not a valid URL";
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
