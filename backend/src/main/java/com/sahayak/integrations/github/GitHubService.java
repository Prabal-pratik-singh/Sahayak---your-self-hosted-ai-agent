package com.sahayak.integrations.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Talks directly to GitHub's official API — no middleman.
 *
 * The server owner registers ONE GitHub OAuth app (README explains how); every
 * user then connects their OWN GitHub account through the normal consent
 * screen, and the agent acts as them (create issues, list repos, search).
 *
 * GitHub OAuth-app access tokens do not expire, so no refresh/expiry handling
 * is needed (unlike LinkedIn).
 */
@Service
public class GitHubService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String API = "https://api.github.com";
    // repo = create issues (incl. private repos); read:user = identify the account
    private static final String SCOPES = "repo read:user";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(@JsonProperty("access_token") String accessToken, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String login, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repo(@JsonProperty("full_name") String fullName, String description,
                       @JsonProperty("html_url") String htmlUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IssueResponse(@JsonProperty("html_url") String htmlUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(Item[] items) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Item(String title, @JsonProperty("html_url") String htmlUrl) {
        }
    }

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;

    public GitHubService(RestClient.Builder restClientBuilder,
                         @Value("${app.github-oauth.client-id:}") String clientId,
                         @Value("${app.github-oauth.client-secret:}") String clientSecret,
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

    /** Must be registered as the "Authorization callback URL" in the GitHub OAuth app. */
    public String redirectUri() {
        return baseUrl + "/api/integrations/github/callback";
    }

    public String authorizeUrl(String state) {
        return AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri())
                + "&state=" + encode(state)
                + "&scope=" + encode(SCOPES);
    }

    public Token exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri());

        return http.post()
                .uri(TOKEN_URL)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE) // ask for JSON, not form text
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Token.class);
    }

    public Account account(String token) {
        return http.get().uri(API + "/user").headers(h -> auth(h, token)).retrieve().body(Account.class);
    }

    /** Creates an issue; returns its URL. repo is "owner/name". */
    public String createIssue(String token, String repo, String title, String body) {
        IssueResponse resp = http.post()
                .uri(API + "/repos/" + repo + "/issues")
                .headers(h -> auth(h, token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "body", body == null ? "" : body))
                .retrieve()
                .body(IssueResponse.class);
        return resp != null ? resp.htmlUrl() : "(created)";
    }

    /** The user's most recently updated repositories. */
    public Repo[] listRepos(String token) {
        Repo[] repos = http.get()
                .uri(API + "/user/repos?sort=updated&per_page=10&affiliation=owner,collaborator,organization_member")
                .headers(h -> auth(h, token))
                .retrieve()
                .body(Repo[].class);
        return repos != null ? repos : new Repo[0];
    }

    /** Free-text issue/PR search across GitHub, scoped by the query the model builds. */
    public String searchIssues(String token, String query) {
        SearchResponse resp = http.get()
                .uri(API + "/search/issues?per_page=5&q=" + encode(query))
                .headers(h -> auth(h, token))
                .retrieve()
                .body(SearchResponse.class);
        if (resp == null || resp.items() == null || resp.items().length == 0) {
            return "No matching issues found.";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchResponse.Item it : resp.items()) {
            sb.append("- ").append(it.title()).append(" (").append(it.htmlUrl()).append(")\n");
        }
        return sb.toString().trim();
    }

    private void auth(HttpHeaders h, String token) {
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        h.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
        h.set("X-GitHub-Api-Version", "2022-11-28");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
