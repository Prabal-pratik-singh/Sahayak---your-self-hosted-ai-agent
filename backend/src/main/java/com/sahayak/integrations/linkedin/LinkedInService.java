package com.sahayak.integrations.linkedin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private static final String ASSETS_URL = "https://api.linkedin.com/v2/assets?action=registerUpload";
    // The classic ugcPosts API above renders only ONE image per post. Posts
    // with SEVERAL images need LinkedIn's newer versioned API (below), which
    // requires a LinkedIn-Version header (YYYYMM). LinkedIn sunsets each
    // version after ~1 year, so a hardcoded value WILL eventually die — we
    // try these candidates newest-first, skip any LinkedIn rejects as
    // inactive, and remember the one that works. LINKEDIN_API_VERSION in the
    // environment overrides the list if LinkedIn ever outpaces it.
    private static final String REST_IMAGES_URL = "https://api.linkedin.com/rest/images?action=initializeUpload";
    private static final String REST_POSTS_URL = "https://api.linkedin.com/rest/posts";
    private static final List<String> VERSION_CANDIDATES = List.of("202606", "202601", "202507");
    private static final String SCOPES = "openid profile email w_member_social";

    /** The last LinkedIn-Version that worked — tried first on later calls. */
    private volatile String knownGoodVersion;

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
    private final String apiVersionOverride;

    public LinkedInService(RestClient.Builder restClientBuilder,
                           @Value("${app.linkedin.client-id:}") String clientId,
                           @Value("${app.linkedin.client-secret:}") String clientSecret,
                           @Value("${app.base-url:http://localhost:8080}") String baseUrl,
                           @Value("${app.linkedin.api-version:}") String apiVersionOverride) {
        this.http = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
        this.apiVersionOverride = apiVersionOverride;
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
                "visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));

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

    /**
     * Publishes a post with an image, following LinkedIn's official 3-step
     * flow: register an upload slot for the member, PUT the raw image bytes to
     * the returned URL, then create the ugcPost referencing the asset URN.
     * Returns the post's URL. The text-only {@link #post} stays untouched.
     */
    public String postWithImage(String accessToken, String personSub, String text,
                                byte[] imageBytes, String imageMime) {
        String author = "urn:li:person:" + personSub;

        // 1) register the image upload
        Map<String, Object> register = Map.of("registerUploadRequest", Map.of(
                "recipes", List.of("urn:li:digitalmediaRecipe:feedshare-image"),
                "owner", author,
                "serviceRelationships", List.of(Map.of(
                        "relationshipType", "OWNER",
                        "identifier", "urn:li:userGeneratedContent"))));

        JsonNode registered = http.post()
                .uri(ASSETS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(register)
                .retrieve()
                .body(JsonNode.class);

        String uploadUrl = registered.at(
                "/value/uploadMechanism/com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest/uploadUrl")
                .asText("");
        String asset = registered.at("/value/asset").asText("");
        if (uploadUrl.isEmpty() || asset.isEmpty()) {
            throw new IllegalStateException("LinkedIn did not return an upload URL for the image.");
        }

        // 2) upload the raw bytes
        http.put()
                .uri(uploadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.parseMediaType(imageMime))
                .body(imageBytes)
                .retrieve()
                .toBodilessEntity();

        // 3) publish the post referencing the uploaded asset
        Map<String, Object> body = Map.of(
                "author", author,
                "lifecycleState", "PUBLISHED",
                "specificContent", Map.of("com.linkedin.ugc.ShareContent", Map.of(
                        "shareCommentary", Map.of("text", text),
                        "shareMediaCategory", "IMAGE",
                        "media", List.of(Map.of(
                                "status", "READY",
                                "media", asset)))),
                "visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));

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

    /** One image ready for upload: its raw bytes and MIME type. */
    public record ImagePayload(byte[] bytes, String mime) {
    }

    /**
     * Publishes a post with one OR several images. A single image goes through
     * the proven ugcPosts flow ({@link #postWithImage}); two or more use the
     * versioned Posts API's multiImage content, which is the only way LinkedIn
     * renders a true multi-image post.
     */
    public String postWithImages(String accessToken, String personSub, String text, List<ImagePayload> images) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("No images to post.");
        }
        if (images.size() == 1) {
            return postWithImage(accessToken, personSub, text, images.get(0).bytes(), images.get(0).mime());
        }

        // LinkedIn retires API versions over time: walk the candidates until
        // one is accepted, and remember it for the next post.
        RestClientResponseException versionRejected = null;
        for (String version : versionsToTry()) {
            try {
                String url = postMultiImage(version, accessToken, personSub, text, images);
                knownGoodVersion = version;
                return url;
            } catch (RestClientResponseException e) {
                if (isVersionNotActive(e)) {
                    versionRejected = e;
                    continue; // sunset version — try the next candidate
                }
                throw e;
            }
        }
        throw versionRejected != null ? versionRejected
                : new IllegalStateException("No LinkedIn API version candidates configured.");
    }

    /** Configured override first, then the last known-good, then the built-in list. */
    private List<String> versionsToTry() {
        List<String> versions = new java.util.ArrayList<>();
        if (apiVersionOverride != null && !apiVersionOverride.isBlank()) {
            versions.add(apiVersionOverride.trim());
        }
        String remembered = knownGoodVersion;
        if (remembered != null && !versions.contains(remembered)) {
            versions.add(remembered);
        }
        for (String candidate : VERSION_CANDIDATES) {
            if (!versions.contains(candidate)) {
                versions.add(candidate);
            }
        }
        return versions;
    }

    /** LinkedIn's rejection of a sunset/unknown LinkedIn-Version header. */
    private static boolean isVersionNotActive(RestClientResponseException e) {
        String body = e.getResponseBodyAsString().toLowerCase();
        return body.contains("version") && (body.contains("is not active") || body.contains("not supported"));
    }

    private String postMultiImage(String linkedInVersion, String accessToken, String personSub,
                                  String text, List<ImagePayload> images) {
        String author = "urn:li:person:" + personSub;

        // 1) initialize + upload every image, collecting its urn:li:image URN
        List<Map<String, String>> imageRefs = new java.util.ArrayList<>();
        for (ImagePayload image : images) {
            JsonNode initialized = http.post()
                    .uri(REST_IMAGES_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("LinkedIn-Version", linkedInVersion)
                    .header("X-Restli-Protocol-Version", "2.0.0")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("initializeUploadRequest", Map.of("owner", author)))
                    .retrieve()
                    .body(JsonNode.class);

            String uploadUrl = initialized.at("/value/uploadUrl").asText("");
            String imageUrn = initialized.at("/value/image").asText("");
            if (uploadUrl.isEmpty() || imageUrn.isEmpty()) {
                throw new IllegalStateException("LinkedIn did not return an upload URL for one of the images.");
            }

            http.put()
                    .uri(uploadUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(image.bytes())
                    .retrieve()
                    .toBodilessEntity();

            imageRefs.add(Map.of("id", imageUrn));
        }

        // 2) one post referencing all uploaded images
        Map<String, Object> body = Map.of(
                "author", author,
                "commentary", escapeLittleText(text),
                "visibility", "PUBLIC",
                "distribution", Map.of(
                        "feedDistribution", "MAIN_FEED",
                        "targetEntities", List.of(),
                        "thirdPartyDistributionChannels", List.of()),
                "content", Map.of("multiImage", Map.of("images", imageRefs)),
                "lifecycleState", "PUBLISHED",
                "isReshareDisabledByViewer", false);

        ResponseEntity<String> response = http.post()
                .uri(REST_POSTS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("LinkedIn-Version", linkedInVersion)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        String postId = response.getHeaders().getFirst("x-restli-id");
        return postId != null ? "https://www.linkedin.com/feed/update/" + postId : "(published)";
    }

    /**
     * The versioned Posts API parses "commentary" as Little Text markup, where
     * these characters are reserved — a caption containing a bare "(" or "@"
     * would be rejected or mangled, so each one is backslash-escaped to render
     * literally. (The classic ugcPosts flow needs no escaping.)
     */
    static String escapeLittleText(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if ("\\|{}@[]()<>#*_~".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
