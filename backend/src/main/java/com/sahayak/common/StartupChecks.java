package com.sahayak.common;

import com.sahayak.agent.AiModelRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Fails fast with a clear message instead of a confusing error on the first chat. */
@Component
public class StartupChecks implements ApplicationRunner {

    private final AiModelRegistry registry;

    public StartupChecks(AiModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (registry.isEmpty()) {
            throw new IllegalStateException("""

                    No AI provider is configured, so the agent has no brain.
                    Set at least ONE of these API keys before starting:
                      ANTHROPIC_API_KEY   (Claude   — https://console.anthropic.com)
                      OPENAI_API_KEY      (ChatGPT  — https://platform.openai.com)
                      GEMINI_API_KEY      (Gemini   — https://aistudio.google.com/apikey)
                    How to set it:
                      PowerShell:  $env:ANTHROPIC_API_KEY = "sk-ant-..."
                      Linux/macOS: export ANTHROPIC_API_KEY=sk-ant-...
                      Docker:      put it in the .env file next to docker-compose.yml
                    """);
        }
    }
}
