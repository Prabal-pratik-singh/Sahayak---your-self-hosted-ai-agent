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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONE place that knows every supported AI engine: how to build a client
 * for it, and — crucially for honesty — whether it can use TOOLS ("actions":
 * scheduling, email, weather, …) or can only chat. Chat-only engines never
 * even receive tool definitions, because several free endpoints reject them.
 *
 * Most engines speak the OpenAI protocol, so they reuse the OpenAI client
 * pointed at a different address.
 */
@Component
public class ChatClientFactory {

    /**
     * Everything the app needs to know about one engine.
     *
     * @param toolCapable   can it take actions (scheduling, email, …)?
     * @param visionCapable can it SEE images? Text-only models (Groq/Cerebras
     *                      llama-3.3) must never receive image input.
     */
    public record ProviderSpec(String id, String label, boolean toolCapable, boolean visionCapable) {
    }

    private static final Map<String, ProviderSpec> SPECS = new LinkedHashMap<>() {{
        put("anthropic", new ProviderSpec("anthropic", "Claude", true, true));
        put("openai", new ProviderSpec("openai", "ChatGPT", true, true));
        put("gemini", new ProviderSpec("gemini", "Gemini", true, true));
        put("groq", new ProviderSpec("groq", "Groq", true, false));
        // GitHub Models' default model here (gpt-4o-mini) is multimodal.
        put("github", new ProviderSpec("github", "GitHub Models", true, true));
        put("cerebras", new ProviderSpec("cerebras", "Cerebras", true, false));
        // Conservative: mistral-small + OpenRouter's free models are treated
        // as text-only rather than risking a hard provider error mid-chat.
        put("mistral", new ProviderSpec("mistral", "Mistral", true, false));
        // OpenRouter's FREE models mostly reject tool definitions, so it is
        // offered honestly as chat-only (its default model here is a free one).
        put("openrouter", new ProviderSpec("openrouter", "OpenRouter", false, false));
    }};

    public static final List<String> KNOWN_IDS = List.copyOf(SPECS.keySet());

    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final String anthropicModel;
    private final String openaiModel;
    private final String geminiModel;
    private final String groqModel;
    private final String githubModel;
    private final String cerebrasModel;
    private final String mistralModel;
    private final String openrouterModel;

    public ChatClientFactory(ChatMemory chatMemory,
                             @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-5}") String anthropicModel,
                             @Value("${spring.ai.openai.chat.options.model:gpt-5-mini}") String openaiModel,
                             @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String geminiModel,
                             @Value("${app.groq.model:openai/gpt-oss-120b}") String groqModel,
                             @Value("${app.github.model:openai/gpt-4o-mini}") String githubModel,
                             @Value("${app.cerebras.model:llama-3.3-70b}") String cerebrasModel,
                             @Value("${app.mistral.model:mistral-small-latest}") String mistralModel,
                             @Value("${app.openrouter.model:meta-llama/llama-3.3-70b-instruct:free}") String openrouterModel) {
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.anthropicModel = anthropicModel;
        this.openaiModel = openaiModel;
        this.geminiModel = geminiModel;
        this.groqModel = groqModel;
        this.githubModel = githubModel;
        this.cerebrasModel = cerebrasModel;
        this.mistralModel = mistralModel;
        this.openrouterModel = openrouterModel;
    }

    public boolean known(String providerId) {
        return providerId != null && SPECS.containsKey(providerId);
    }

    public String labelOf(String providerId) {
        ProviderSpec spec = SPECS.get(providerId);
        return spec != null ? spec.label() : providerId;
    }

    /** Can this engine take actions (tools), or only talk? */
    public boolean toolCapable(String providerId) {
        ProviderSpec spec = SPECS.get(providerId);
        return spec != null && spec.toolCapable();
    }

    /** Can this engine see images? Guard EVERY image send with this. */
    public boolean visionCapable(String providerId) {
        ProviderSpec spec = SPECS.get(providerId);
        return spec != null && spec.visionCapable();
    }

    public String modelOf(String providerId) {
        return switch (providerId) {
            case "anthropic" -> anthropicModel;
            case "openai" -> openaiModel;
            case "gemini" -> geminiModel;
            case "groq" -> groqModel;
            case "github" -> githubModel;
            case "cerebras" -> cerebrasModel;
            case "mistral" -> mistralModel;
            case "openrouter" -> openrouterModel;
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
            case "gemini" -> GoogleGenAiChatModel.builder()
                    .genAiClient(Client.builder().apiKey(apiKey).build())
                    .defaultOptions(GoogleGenAiChatOptions.builder().model(geminiModel).build())
                    .build();
            case "openai" -> openAiStyle(apiKey, null, null, openaiModel);
            case "groq" -> openAiStyle(apiKey, "https://api.groq.com/openai", null, groqModel);
            // GitHub Models skips the usual /v1 prefix, hence the custom path.
            case "github" -> openAiStyle(apiKey, "https://models.github.ai/inference", "/chat/completions", githubModel);
            case "cerebras" -> openAiStyle(apiKey, "https://api.cerebras.ai", null, cerebrasModel);
            case "mistral" -> openAiStyle(apiKey, "https://api.mistral.ai", null, mistralModel);
            case "openrouter" -> openAiStyle(apiKey, "https://openrouter.ai/api", null, openrouterModel);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown AI provider '" + providerId + "'.");
        };
    }

    private OpenAiChatModel openAiStyle(String apiKey, String baseUrl, String completionsPath, String modelName) {
        OpenAiApi.Builder api = OpenAiApi.builder().apiKey(apiKey);
        if (baseUrl != null) {
            api.baseUrl(baseUrl);
        }
        if (completionsPath != null) {
            api.completionsPath(completionsPath);
        }
        return OpenAiChatModel.builder()
                .openAiApi(api.build())
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }
}
