package com.sahayak.agent;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * Keeps the last 30 messages per conversation. The repository is the
     * JDBC one (auto-configured), so chat history lives in Postgres and
     * survives restarts.
     */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(30)
                .build();
    }

    /**
     * One ChatClient per AI provider whose API key is set. Tools are NOT
     * registered here — AgentService attaches them per request, scoped to
     * the logged-in user.
     */
    @Bean
    AiModelRegistry aiModelRegistry(ObjectProvider<AnthropicChatModel> anthropic,
                                    ChatMemory chatMemory,
                                    @Value("${spring.ai.anthropic.api-key:}") String anthropicKey,
                                    @Value("${spring.ai.openai.api-key:}") String openaiKey,
                                    @Value("${spring.ai.google.genai.api-key:}") String geminiKey,
                                    @Value("${spring.ai.anthropic.chat.options.model:}") String anthropicModel,
                                    @Value("${spring.ai.openai.chat.options.model:gpt-5-mini}") String openaiModel,
                                    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String geminiModel,
                                    @Value("${app.default-ai:}") String preferredDefault) {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        var providers = new LinkedHashMap<String, AiModelRegistry.Provider>();

        if (!anthropicKey.isBlank()) {
            anthropic.ifAvailable(model -> providers.put("anthropic",
                    provider("anthropic", "Claude", anthropicModel, model, memoryAdvisor)));
        }
        // OpenAI and Gemini are built by hand (their auto-configs are excluded)
        // because those crash the whole app when their key is blank — and every
        // provider must stay optional here.
        if (!openaiKey.isBlank()) {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder().apiKey(openaiKey).build())
                    .defaultOptions(OpenAiChatOptions.builder().model(openaiModel).build())
                    .build();
            providers.put("openai", provider("openai", "ChatGPT", openaiModel, model, memoryAdvisor));
        }
        if (!geminiKey.isBlank()) {
            GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                    .genAiClient(Client.builder().apiKey(geminiKey).build())
                    .defaultOptions(GoogleGenAiChatOptions.builder().model(geminiModel).build())
                    .build();
            providers.put("gemini", provider("gemini", "Gemini", geminiModel, model, memoryAdvisor));
        }

        log.info("AI providers configured: {}", providers.isEmpty() ? "NONE" : providers.keySet());
        return new AiModelRegistry(providers, preferredDefault);
    }

    private AiModelRegistry.Provider provider(String id, String label, String modelName,
                                              ChatModel model, MessageChatMemoryAdvisor memoryAdvisor) {
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(memoryAdvisor)
                .build();
        return new AiModelRegistry.Provider(new AiModelRegistry.Option(id, label, modelName), client);
    }
}
