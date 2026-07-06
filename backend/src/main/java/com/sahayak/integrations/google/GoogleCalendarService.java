package com.sahayak.integrations.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Talks directly to Google's official OAuth + Calendar API — no middleman.
 *
 * The server owner registers ONE Google Cloud project (README explains how);
 * every user of this Sahayak instance then connects their OWN Google account
 * through the standard consent screen.
 *
 * Unlike LinkedIn/GitHub, Google access tokens last only ~1 hour — so we also
 * store the refresh token and mint a fresh access token whenever needed
 * (see GoogleCalendarTools). Users connect once.
 */
@Service
public class GoogleCalendarService {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    // calendar.events is a "sensitive" (not "restricted") scope: an unverified
    // Google app works, users just see Google's warning screen once.
    private static final String SCOPES = "https://www.googleapis.com/auth/calendar.events openid email";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("refresh_token") String refreshToken,
                        @JsonProperty("expires_in") long expiresInSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(String email) {
    }

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;

    public GoogleCalendarService(RestClient.Builder restClientBuilder,
                                 @Value("${app.google-oauth.client-id:}") String clientId,
                                 @Value("${app.google-oauth.client-secret:}") String clientSecret,
                                 @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.http = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** Must be registered as an "Authorized redirect URI" in the Google Cloud OAuth client. */
    public String redirectUri() {
        return baseUrl + "/api/integrations/google-calendar/callback";
    }

    public String authorizeUrl(String state) {
        // access_type=offline + prompt=consent → Google always returns a
        // refresh token, so users connect once and never weekly.
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri())
                + "&state=" + encode(state)
                + "&scope=" + encode(SCOPES)
                + "&access_type=offline"
                + "&prompt=consent";
    }

    public Token exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return postForm(form);
    }

    /** Mints a fresh ~1h access token from the stored refresh token. */
    public Token refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return postForm(form);
    }

    public UserInfo userInfo(String accessToken) {
        return http.get()
                .uri(USERINFO_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(UserInfo.class);
    }

    /**
     * Creates an event on the user's primary calendar and returns its link.
     * Times are local date-times interpreted in the server's timezone
     * (TZ env, e.g. Asia/Kolkata), which is what "tomorrow 9 AM" means here.
     */
    public String createEvent(String accessToken, String title, String description,
                              String startIso, String endIso) {
        String zone = ZoneId.systemDefault().getId();
        Map<String, Object> body = new HashMap<>();
        body.put("summary", title);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        body.put("start", Map.of("dateTime", startIso, "timeZone", zone));
        body.put("end", Map.of("dateTime", endIso, "timeZone", zone));

        JsonNode created = http.post()
                .uri(EVENTS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String link = created != null ? created.path("htmlLink").asText("") : "";
        return link.isEmpty() ? "(created)" : link;
    }

    /** Upcoming events on the primary calendar, one per line, oldest first. */
    public String listUpcoming(String accessToken, int days) {
        OffsetDateTime now = OffsetDateTime.now();
        String url = EVENTS_URL
                + "?timeMin=" + encode(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                + "&timeMax=" + encode(now.plusDays(days).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                + "&singleEvents=true&orderBy=startTime&maxResults=15";

        JsonNode response = http.get()
                .uri(java.net.URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        StringBuilder sb = new StringBuilder();
        if (response != null) {
            for (JsonNode item : response.path("items")) {
                // timed events have start.dateTime; all-day events have start.date
                String start = item.path("start").path("dateTime")
                        .asText(item.path("start").path("date").asText(""));
                sb.append("- ").append(item.path("summary").asText("(no title)"))
                        .append(" — ").append(start).append('\n');
            }
        }
        return sb.toString().strip();
    }

    private Token postForm(MultiValueMap<String, String> form) {
        return http.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Token.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
