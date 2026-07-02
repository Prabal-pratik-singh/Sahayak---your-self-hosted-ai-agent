package com.sahayak.integrations.email;

/**
 * A user's own outgoing-mail (SMTP) account. Works with any provider:
 * Gmail (app password), Outlook, Zoho, a company mail server, ...
 */
public record EmailSettings(String host, int port, String username, String password, String fromAddress) {

    /** The address mails are sent from — defaults to the login username. */
    public String effectiveFrom() {
        return (fromAddress == null || fromAddress.isBlank()) ? username : fromAddress;
    }
}
