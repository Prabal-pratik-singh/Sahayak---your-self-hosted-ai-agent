package com.sahayak.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

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
     * The SERVER's own providers — one client per configured key, all built
     * through the same factory that also serves user-provided keys (BYOK).
     * All vendor auto-configurations are excluded (they crash on blank keys),
     * so every provider stays optional: a server can even run with ZERO keys
     * and let users bring their own.
     */
    @Bean
    AiModelRegistry aiModelRegistry(ChatClientFactory factory,
                                    @Value("${spring.ai.anthropic.api-key:}") String anthropicKey,
                                    @Value("${spring.ai.openai.api-key:}") String openaiKey,
                                    @Value("${spring.ai.google.genai.api-key:}") String geminiKey,
                                    @Value("${app.groq.api-key:}") String groqKey,
                                    @Value("${app.default-ai:}") String preferredDefault) {
        Map<String, String> serverKeys = new LinkedHashMap<>();
        serverKeys.put("anthropic", anthropicKey);
        serverKeys.put("openai", openaiKey);
        serverKeys.put("gemini", geminiKey);
        serverKeys.put("groq", groqKey);

        var providers = new LinkedHashMap<String, AiModelRegistry.Provider>();
        serverKeys.forEach((id, key) -> {
            if (key != null && !key.isBlank()) {
                providers.put(id, new AiModelRegistry.Provider(
                        new AiModelRegistry.Option(id, factory.labelOf(id), factory.modelOf(id)),
                        factory.create(id, key.trim())));
            }
        });

        log.info("AI providers with server keys: {}", providers.isEmpty() ? "NONE (BYOK-only mode)" : providers.keySet());
        return new AiModelRegistry(providers, preferredDefault);
    }
}
