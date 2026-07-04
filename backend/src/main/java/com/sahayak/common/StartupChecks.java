package com.sahayak.common;

import com.sahayak.agent.AiModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * With BYOK, a server without any AI key is legal (users bring their own) —
 * so this warns loudly instead of refusing to start.
 */
@Component
public class StartupChecks implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);

    private final AiModelRegistry registry;

    public StartupChecks(AiModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (registry.isEmpty()) {
            log.warn("""

                    No server AI key is configured — running in BYOK-only mode.
                    Users must add their own key in Settings → AI engine keys before they can chat.
                    To give everyone a default engine, set one of:
                      ANTHROPIC_API_KEY   (Claude   — https://console.anthropic.com)
                      OPENAI_API_KEY      (ChatGPT  — https://platform.openai.com)
                      GEMINI_API_KEY      (Gemini   — https://aistudio.google.com/apikey)
                      GROQ_API_KEY        (Groq     — https://console.groq.com/keys, generous free tier)
                    """);
        }
    }
}
