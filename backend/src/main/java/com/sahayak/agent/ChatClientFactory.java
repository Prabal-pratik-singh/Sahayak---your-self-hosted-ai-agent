package com.sahayak.agent;

import com.google.genai.Client;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Builds a ready ChatClient for any supported provider from an API key —
 * the ONE place that knows how to talk to each vendor. Used for the server's
 * own keys (at startup) and for every user-provided key (BYOK, on demand).
 *
 * Groq needs no SDK of its own: it speaks the OpenAI protocol, so it is the
 * OpenAI client pointed at Groq's servers.
 */
@Component
public class ChatClientFactory {

    public static final List<String> KNOWN_IDS = List.of("anthropic", "openai", "gemini", "groq");

    private static final Map<String, String> LABELS = Map.of(
            "anthropic", "Claude",
            "openai", "ChatGPT",
            "gemini", "Gemini",
            "groq", "Groq");

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai";

    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final String anthropicModel;
    private final String openaiModel;
    private final String geminiModel;
    private final String groqModel;

    public ChatClientFactory(ChatMemory chatMemory,
                             @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-5}") String anthropicModel,
                             @Value("${spring.ai.openai.chat.options.model:gpt-5-mini}") String openaiModel,
                             @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String geminiModel,
                             @Value("${app.groq.model:llama-3.3-70b-versatile}") String groqModel) {
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.anthropicModel = anthropicModel;
        this.openaiModel = openaiModel;
        this.geminiModel = geminiModel;
        this.groqModel = groqModel;
    }

    public boolean known(String providerId) {
        return providerId != null && LABELS.containsKey(providerId);
    }

    public String labelOf(String providerId) {
        return LABELS.getOrDefault(providerId, providerId);
    }

    public String modelOf(String providerId) {
        return switch (providerId) {
            case "anthropic" -> anthropicModel;
            case "openai" -> openaiModel;
            case "gemini" -> geminiModel;
            case "groq" -> groqModel;
            default -> "?";
        };
    }

    /** Builds a chat client (with shared conversation memory) for this provider + key. */
    public ChatClient create(String providerId, String apiKey) {
        return ChatClient.builder(buildModel(providerId, apiKey)).defaultAdvisors(memoryAdvisor).build();
    }

    /** A bare client with NO memory — for key verification pings that must not touch chat history. */
    public ChatClient createBare(String providerId, String apiKey) {
        return ChatClient.builder(buildModel(providerId, apiKey)).build();
    }

    private ChatModel buildModel(String providerId, String apiKey) {
        return switch (providerId) {
            case "anthropic" -> AnthropicChatModel.builder()
                    .anthropicApi(AnthropicApi.builder().apiKey(apiKey).build())
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(anthropicModel).maxTokens(4096).build())
                    .build();
            case "openai" -> openAiStyle(apiKey, null, openaiModel);
            case "groq" -> openAiStyle(apiKey, GROQ_BASE_URL, groqModel);
            case "gemini" -> GoogleGenAiChatModel.builder()
                    .genAiClient(Client.builder().apiKey(apiKey).build())
                    .defaultOptions(GoogleGenAiChatOptions.builder().model(geminiModel).build())
                    .build();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown AI provider '" + providerId + "'.");
        };
    }

    private OpenAiChatModel openAiStyle(String apiKey, String baseUrl, String modelName) {
        OpenAiApi.Builder api = OpenAiApi.builder().apiKey(apiKey);
        if (baseUrl != null) {
            api.baseUrl(baseUrl);
        }
        return OpenAiChatModel.builder()
                .openAiApi(api.build())
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }
}
