package com.sahayak.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The agent's window to the internet — a controlled tool layer, not raw
 * browser access. Weather and Wikipedia hit fixed, trusted, key-free APIs;
 * fetchWebPage goes through {@link UrlGuard} and never follows redirects,
 * so the model cannot be tricked into reaching the local network.
 *
 * Stateless and credential-free, so one shared instance serves all users.
 */
@Component
public class WebTools {

    private static final String USER_AGENT = "Sahayak/0.3 (self-hosted personal agent)";
    private static final int MAX_PAGE_CHARS = 6000;
    private static final int MAX_HTML_CHARS = 400_000;

    private final RestClient http;        // trusted, fixed APIs (Open-Meteo, Wikipedia)
    private final RestClient pageFetcher; // arbitrary user URLs: guarded, no redirects
    private final ObjectMapper mapper;

    public WebTools(RestClient.Builder restClientBuilder, ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = restClientBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
        HttpClient noRedirectClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(noRedirectClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.pageFetcher = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    @Tool(description = """
            Get the current weather and a short 2-day forecast for any city or place, worldwide. \
            ALWAYS use this when the user asks about weather — never claim you lack live data.""")
    public String getWeather(
            @ToolParam(description = "City or place name, e.g. 'Delhi' or 'Mumbai, India'") String place) {
        try {
            JsonNode results = mapper.readTree(http.get()
                            .uri("https://geocoding-api.open-meteo.com/v1/search?name={place}&count=1&language=en&format=json", place)
                            .retrieve().body(String.class))
                    .path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "ERROR: could not find a place called '" + place + "'. Ask the user to be more specific.";
            }
            JsonNode spot = results.get(0);
            StringBuilder label = new StringBuilder(spot.path("name").asText());
            if (spot.hasNonNull("admin1")) {
                label.append(", ").append(spot.path("admin1").asText());
            }
            if (spot.hasNonNull("country")) {
                label.append(", ").append(spot.path("country").asText());
            }

            JsonNode weather = mapper.readTree(http.get()
                    .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                                    + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
                                    + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                                    + "&forecast_days=2&timezone=auto",
                            spot.path("latitude").asDouble(), spot.path("longitude").asDouble())
                    .retrieve().body(String.class));
            JsonNode current = weather.path("current");
            JsonNode daily = weather.path("daily");

            return "Weather in " + label + " right now (" + current.path("time").asText().replace('T', ' ')
                    + " local time): " + describeWeatherCode(current.path("weather_code").asInt())
                    + ", " + current.path("temperature_2m").asText() + "°C"
                    + " (feels like " + current.path("apparent_temperature").asText() + "°C),"
                    + " humidity " + current.path("relative_humidity_2m").asText() + "%,"
                    + " wind " + current.path("wind_speed_10m").asText() + " km/h."
                    + " Today: " + daily.path("temperature_2m_min").path(0).asText()
                    + " to " + daily.path("temperature_2m_max").path(0).asText() + "°C,"
                    + " rain chance " + daily.path("precipitation_probability_max").path(0).asText() + "%."
                    + " Tomorrow: " + daily.path("temperature_2m_min").path(1).asText()
                    + " to " + daily.path("temperature_2m_max").path(1).asText() + "°C,"
                    + " rain chance " + daily.path("precipitation_probability_max").path(1).asText() + "%.";
        } catch (Exception e) {
            return "ERROR: weather lookup failed: " + e.getMessage();
        }
    }

    @Tool(description = """
            Search Wikipedia for people, places, organisations, events or concepts. \
            Good for factual background. Returns the top matches with a summary and link.""")
    public String searchWikipedia(
            @ToolParam(description = "What to look up") String query) {
        try {
            JsonNode pages = mapper.readTree(http.get()
                            .uri("https://en.wikipedia.org/w/rest.php/v1/search/page?q={q}&limit=3", query)
                            .retrieve().body(String.class))
                    .path("pages");
            if (!pages.isArray() || pages.isEmpty()) {
                return "No Wikipedia results for '" + query + "'.";
            }
            StringBuilder out = new StringBuilder("Wikipedia results for '").append(query).append("':\n");
            for (JsonNode page : pages) {
                String title = page.path("title").asText();
                out.append("- ").append(title);
                if (page.hasNonNull("description")) {
                    out.append(" — ").append(page.path("description").asText());
                }
                String excerpt = htmlToText(page.path("excerpt").asText(""));
                if (!excerpt.isBlank()) {
                    out.append(". ").append(excerpt);
                }
                out.append(" (https://en.wikipedia.org/wiki/").append(title.replace(' ', '_')).append(")\n");
            }
            return out.toString();
        } catch (Exception e) {
            return "ERROR: Wikipedia search failed: " + e.getMessage();
        }
    }

    @Tool(description = """
            Fetch a public web page and return its readable text (truncated). Use when the user \
            gives you a URL or you need the content of a specific public page. \
            Private/internal addresses are blocked for safety.""")
    public String fetchWebPage(
            @ToolParam(description = "Full http(s) URL, e.g. https://example.com/article") String url) {
        String problem = UrlGuard.check(url);
        if (problem != null) {
            return "ERROR: " + problem + ".";
        }
        try {
            ResponseEntity<String> response = pageFetcher.get()
                    .uri(URI.create(url))
                    .header(HttpHeaders.ACCEPT, "text/html, text/plain;q=0.9, */*;q=0.5")
                    .retrieve()
                    .onStatus(status -> true, (request, resp) -> { /* judge the status ourselves below */ })
                    .toEntity(String.class);

            if (response.getStatusCode().is3xxRedirection()) {
                URI location = response.getHeaders().getLocation();
                return location != null
                        ? "ERROR: that page redirects to " + location + " — fetch that URL instead."
                        : "ERROR: that page redirects elsewhere and gave no target.";
            }
            if (response.getStatusCode().isError()) {
                return "ERROR: the page returned HTTP " + response.getStatusCode().value() + ".";
            }
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return "ERROR: the page returned no content.";
            }
            String text = htmlToText(body);
            if (text.length() > MAX_PAGE_CHARS) {
                text = text.substring(0, MAX_PAGE_CHARS) + " …[truncated]";
            }
            return "Content of " + url + ":\n" + text;
        } catch (Exception e) {
            return "ERROR: could not fetch that page: " + e.getMessage();
        }
    }

    /** WMO weather code → plain English. */
    static String describeWeatherCode(int code) {
        if (code == 0) return "clear sky";
        if (code <= 2) return "partly cloudy";
        if (code == 3) return "overcast";
        if (code == 45 || code == 48) return "foggy";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "rain showers";
        if (code == 85 || code == 86) return "snow showers";
        if (code >= 95) return "thunderstorm";
        return "mixed conditions";
    }

    /** Very small HTML→text: good enough to hand page content to a language model. */
    static String htmlToText(String html) {
        if (html.length() > MAX_HTML_CHARS) {
            html = html.substring(0, MAX_HTML_CHARS);
        }
        String text = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?is)</(p|div|h[1-6]|li|tr|section|article)>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{2,}", "\n")
                .strip();
    }
}
