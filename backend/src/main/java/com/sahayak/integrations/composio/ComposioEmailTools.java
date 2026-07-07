package com.sahayak.integrations.composio;

import com.sahayak.activity.ActivityService;
import com.sahayak.integrations.ConnectionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The email tool for users who connected Gmail through Composio instead of
 * direct SMTP. Deliberately exposes the SAME tool name and shape as
 * {@link com.sahayak.integrations.email.EmailTools#sendEmail}, so the model's
 * behavior is identical whichever path the user chose. AgentService registers
 * exactly one of the two per request.
 */
public class ComposioEmailTools {

    private final ComposioService composioService;
    private final ConnectionService.ComposioGmailAccount account;
    private final ActivityService activity;
    private final Long userId;

    public ComposioEmailTools(ComposioService composioService,
                              ConnectionService.ComposioGmailAccount account,
                              ActivityService activity,
                              Long userId) {
        this.composioService = composioService;
        this.account = account;
        this.activity = activity;
        this.userId = userId;
    }

    @Tool(description = """
            Send an email from the user's connected Gmail account. \
            Only call this after the user has confirmed the exact recipient, subject and body \
            in this conversation, or when executing an already-approved scheduled task.""")
    public String sendEmail(
            @ToolParam(description = "Recipient email address (one address)") String to,
            @ToolParam(description = "Subject line") String subject,
            @ToolParam(description = "Plain-text body of the email") String body) {
        try {
            String problem = composioService.sendGmail(account.userTag(), account.connectedAccountId(),
                    to, subject, body);
            if (problem == null) {
                activity.record(userId, "email", "Sent an email to " + to);
                return "Email sent to " + to + ".";
            }
            return "ERROR: could not send the email: " + problem;
        } catch (Exception e) {
            return "ERROR: could not send the email: " + e.getMessage();
        }
    }
}
