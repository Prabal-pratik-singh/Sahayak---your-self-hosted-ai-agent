package com.sahayak.integrations.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Talks to the official Telegram Bot API. Each user connects their OWN bot
 * (from @BotFather) plus the chat id it should write to — completely free,
 * no app review needed. The token never appears in logs or API responses.
 */
@Service
public class TelegramService {

    private static final String API = "https://api.telegram.org/bot";

    private final RestClient http;
    private final ObjectMapper mapper;

    public TelegramService(RestClient.Builder builder, ObjectMapper mapper) {
        this.http = builder.build();
        this.mapper = mapper;
    }

    /** Checks the bot token is real and the chat is reachable; returns the bot's username. */
    public String verify(String botToken, String chatId) {
        JsonNode me = call(botToken, "getMe", null);
        if (!me.path("ok").asBoolean()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Telegram rejected that bot token. Copy it exactly from @BotFather.");
        }
        String username = me.path("result").path("username").asText("bot");
        JsonNode sent = call(botToken, "sendMessage",
                Map.of("chat_id", chatId, "text", "✅ Sahayak connected to this chat."));
        if (!sent.path("ok").asBoolean()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The bot works, but that chat id did not: " + sent.path("description").asText("unknown error")
                            + ". Send your bot a message first, then check the id (see the hint below the form).");
        }
        return username;
    }

    /** Sends a message; returns null on success or a human-readable problem. */
    public String send(String botToken, String chatId, String text) {
        JsonNode sent = call(botToken, "sendMessage",
                Map.of("chat_id", chatId, "text", truncate(text)));
        return sent.path("ok").asBoolean() ? null : sent.path("description").asText("unknown Telegram error");
    }

    private JsonNode call(String botToken, String method, Map<String, Object> body) {
        try {
            var request = http.post().uri(API + botToken + "/" + method);
            if (body != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            String response = request.retrieve()
                    .onStatus(status -> true, (req, resp) -> { /* Telegram sends JSON even on errors */ })
                    .body(String.class);
            return mapper.readTree(response == null ? "{}" : response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Telegram: " + e.getMessage());
        }
    }

    private static String truncate(String text) {
        return text.length() <= 4000 ? text : text.substring(0, 4000) + "…";
    }
}
