package com.sahayak.integrations.linkedin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Talks directly to LinkedIn's official API — no third-party middleman.
 *
 * The server owner registers ONE LinkedIn app (README explains how); every
 * user of this Sahayak instance can then connect their OWN LinkedIn account
 * through the standard OAuth consent screen, and posts are published as them.
 *
 * Note: LinkedIn access tokens last about 60 days and cannot be refreshed on
 * standard apps, so users reconnect when the token expires.
 */
@Service
public class LinkedInService {

    private static final String AUTHORIZE_URL = "https://www.linkedin.com/oauth/v2/authorization";
    private static final String TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken";
    private static final String USERINFO_URL = "https://api.linkedin.com/v2/userinfo";
    private static final String POSTS_URL = "https://api.linkedin.com/v2/ugcPosts";
    private static final String SCOPES = "openid profile email w_member_social";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("expires_in") long expiresInSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(String sub, String name) {
    }

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;

    public LinkedInService(RestClient.Builder restClientBuilder,
                           @Value("${app.linkedin.client-id:}") String clientId,
                           @Value("${app.linkedin.client-secret:}") String clientSecret,
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

    /** Must be registered as an "Authorized redirect URL" in the LinkedIn app. */
    public String redirectUri() {
        return baseUrl + "/api/integrations/linkedin/callback";
    }

    public String authorizeUrl(String state) {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri())
                + "&state=" + encode(state)
                + "&scope=" + encode(SCOPES);
    }

    public Token exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        return http.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Token.class);
    }

    public Profile profile(String accessToken) {
        return http.get()
                .uri(USERINFO_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Profile.class);
    }

    /** Publishes a public text post on the member's profile and returns its URL. */
    public String post(String accessToken, String personSub, String text) {
        Map<String, Object> body = Map.of(
                "author", "urn:li:person:" + personSub,
                "lifecycleState", "PUBLISHED",
                "specificContent", Map.of("com.linkedin.ugc.ShareContent", Map.of(
                        "shareCommentary", Map.of("text", text),
                        "shareMediaCategory", "NONE")),
                "visibility", Map.of("com.linkedin.member.NetworkVisibility", "PUBLIC"));

        ResponseEntity<String> response = http.post()
                .uri(POSTS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        String postId = response.getHeaders().getFirst("X-RestLi-Id");
        return postId != null ? "https://www.linkedin.com/feed/update/" + postId : "(published)";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
