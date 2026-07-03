package com.sahayak.integrations.messaging;

import com.sahayak.integrations.ConnectionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Optional;

/**
 * Messaging tools handed to the LLM — one instance per request, bound to the
 * current user's own Telegram bot / Discord webhook / Slack webhook. A tool
 * for a platform the user has not connected simply reports that, so the model
 * can point them at the Integrations page instead of pretending.
 */
public class MessagingTools {

    private final TelegramService telegramService;
    private final WebhookService webhookService;
    private final Optional<ConnectionService.TelegramConfig> telegram;
    private final Optional<String> discordWebhook;
    private final Optional<String> slackWebhook;

    public MessagingTools(TelegramService telegramService,
                          WebhookService webhookService,
                          Optional<ConnectionService.TelegramConfig> telegram,
                          Optional<String> discordWebhook,
                          Optional<String> slackWebhook) {
        this.telegramService = telegramService;
        this.webhookService = webhookService;
        this.telegram = telegram;
        this.discordWebhook = discordWebhook;
        this.slackWebhook = slackWebhook;
    }

    @Tool(description = """
            Send a message to the user's connected Telegram chat. \
            Only call after the user confirmed the exact text in this conversation, \
            or when executing an already-approved scheduled task.""")
    public String sendTelegramMessage(
            @ToolParam(description = "The message text to send") String text) {
        if (telegram.isEmpty()) {
            return "ERROR: Telegram is not connected. The user can connect it on the Integrations page.";
        }
        String problem = telegramService.send(telegram.get().botToken(), telegram.get().chatId(), text);
        return problem == null ? "Message sent on Telegram." : "ERROR: Telegram said: " + problem;
    }

    @Tool(description = """
            Post a message to the user's connected Discord channel. \
            Only call after the user confirmed the exact text in this conversation, \
            or when executing an already-approved scheduled task.""")
    public String sendDiscordMessage(
            @ToolParam(description = "The message text to post") String text) {
        if (discordWebhook.isEmpty()) {
            return "ERROR: Discord is not connected. The user can connect it on the Integrations page.";
        }
        String problem = webhookService.sendDiscord(discordWebhook.get(), text);
        return problem == null ? "Message posted on Discord." : "ERROR: Discord said: " + problem;
    }

    @Tool(description = """
            Post a message to the user's connected Slack channel. \
            Only call after the user confirmed the exact text in this conversation, \
            or when executing an already-approved scheduled task.""")
    public String sendSlackMessage(
            @ToolParam(description = "The message text to post") String text) {
        if (slackWebhook.isEmpty()) {
            return "ERROR: Slack is not connected. The user can connect it on the Integrations page.";
        }
        String problem = webhookService.sendSlack(slackWebhook.get(), text);
        return problem == null ? "Message posted on Slack." : "ERROR: Slack said: " + problem;
    }
}
