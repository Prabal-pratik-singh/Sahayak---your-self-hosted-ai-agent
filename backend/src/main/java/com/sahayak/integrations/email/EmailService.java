package com.sahayak.integrations.email;

import jakarta.mail.MessagingException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Properties;

/**
 * Sends mail through whatever SMTP account each user connected — there is no
 * global mail account. A fresh sender is built per call from the stored settings.
 */
@Service
public class EmailService {

    /** Tries to log in to the mailbox; called before saving a connection so bad credentials fail early. */
    public void verify(EmailSettings settings) {
        try {
            buildSender(settings).testConnection();
        } catch (MessagingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not log in to that mailbox: " + e.getMessage()
                            + " (for Gmail, use an App Password — see the README)");
        }
    }

    public void send(EmailSettings settings, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.effectiveFrom());
        message.setTo(splitRecipients(to));
        message.setSubject(subject);
        message.setText(body);
        buildSender(settings).send(message);
    }

    private JavaMailSenderImpl buildSender(EmailSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setUsername(settings.username());
        sender.setPassword(settings.password());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        // Port 465 uses implicit TLS; everything else (usually 587) uses STARTTLS.
        if (settings.port() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    private static String[] splitRecipients(String to) {
        return Arrays.stream(to.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }
}
