package com.sahayak.integrations.email;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The email tool handed to the LLM — one instance per request, bound to the
 * current user's own mailbox, so the model can never send from anyone else's account.
 */
public class EmailTools {

    private final EmailService emailService;
    private final EmailSettings settings;

    public EmailTools(EmailService emailService, EmailSettings settings) {
        this.emailService = emailService;
        this.settings = settings;
    }

    @Tool(description = """
            Send an email from the user's connected email account. \
            Only call this after the user has confirmed the exact recipient, subject and body \
            in this conversation, or when executing an already-approved scheduled task.""")
    public String sendEmail(
            @ToolParam(description = "Recipient address; separate several recipients with commas") String to,
            @ToolParam(description = "Subject line") String subject,
            @ToolParam(description = "Plain-text body of the email") String body) {
        try {
            emailService.send(settings, to, subject, body);
            return "Email sent to " + to + ".";
        } catch (Exception e) {
            return "ERROR: could not send the email: " + e.getMessage();
        }
    }
}
