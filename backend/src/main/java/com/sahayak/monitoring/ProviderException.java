package com.sahayak.monitoring;

/** An AI provider call that failed — classified, provider-labeled, user-ready. */
public class ProviderException extends RuntimeException {

    private final String providerId;
    private final String providerLabel;
    private final AiErrorCategory category;
    private final String friendlyMessage;

    public ProviderException(String providerId, String providerLabel, AiErrorCategory category,
                             String friendlyMessage, Throwable cause) {
        super(friendlyMessage, cause);
        this.providerId = providerId;
        this.providerLabel = providerLabel;
        this.category = category;
        this.friendlyMessage = friendlyMessage;
    }

    public String providerId() {
        return providerId;
    }

    public String providerLabel() {
        return providerLabel;
    }

    public AiErrorCategory category() {
        return category;
    }

    public String friendlyMessage() {
        return friendlyMessage;
    }
}
