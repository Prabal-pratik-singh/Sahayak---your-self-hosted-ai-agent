package com.sahayak.monitoring;

import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Turns whatever exception an AI SDK throws into a category and a
 * plain-language sentence. Works on the exception CHAIN and on message
 * text, so it is provider-agnostic: Gemini, OpenAI and Anthropic all wrap
 * their HTTP errors differently, but the words and codes are recognizable.
 */
public final class AiErrorClassifier {

    private AiErrorClassifier() {
    }

    public static AiErrorCategory classify(Throwable error) {
        for (Throwable t = error; t != null; t = next(t)) {
            if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
                return AiErrorCategory.TIMEOUT;
            }
            if (t instanceof UnknownHostException || t instanceof ConnectException
                    || t instanceof ResourceAccessException) {
                return AiErrorCategory.NETWORK;
            }
            String m = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (m.contains("429") || m.contains("quota") || m.contains("rate limit")
                    || m.contains("rate_limit") || m.contains("resource_exhausted")
                    || m.contains("too many requests")) {
                return AiErrorCategory.QUOTA;
            }
            if (m.contains("401") || m.contains("403") || m.contains("api key not valid")
                    || m.contains("invalid api key") || m.contains("invalid x-api-key")
                    || m.contains("incorrect api key") || m.contains("unauthorized")
                    || m.contains("unauthenticated") || m.contains("permission denied")
                    || m.contains("authentication")) {
                return AiErrorCategory.INVALID_KEY;
            }
            // A wrong/retired model name — providers (esp. Groq) rename models
            // often. Check before the generic 5xx bucket.
            if (m.contains("404") || m.contains("model_not_found") || m.contains("model not found")
                    || m.contains("decommission") || m.contains("does not exist")
                    || m.contains("no such model") || m.contains("model_decommissioned")) {
                return AiErrorCategory.MODEL_UNAVAILABLE;
            }
            if (m.contains("503") || m.contains("502") || m.contains("500")
                    || m.contains("overloaded") || m.contains("unavailable")
                    || m.contains("internal server error") || m.contains("bad gateway")) {
                return AiErrorCategory.PROVIDER_DOWN;
            }
            if (m.contains("timed out") || m.contains("timeout")) {
                return AiErrorCategory.TIMEOUT;
            }
        }
        return AiErrorCategory.UNKNOWN;
    }

    /** One clear sentence for the user, naming the provider. */
    public static String friendly(AiErrorCategory category, String providerLabel) {
        return switch (category) {
            case QUOTA -> "You've hit " + providerLabel + "'s usage limit — free tiers cap how many "
                    + "requests you get per minute and per day. Wait a bit and try again, add your own key "
                    + "in Settings, or switch to another AI in the dropdown.";
            case INVALID_KEY -> providerLabel + " rejected the API key — it looks invalid, expired, or "
                    + "missing permissions. The server owner should check that key.";
            case MODEL_UNAVAILABLE -> providerLabel + " no longer offers the configured model (providers "
                    + "rename or retire them). The server owner should update the model name for " + providerLabel + ".";
            case PROVIDER_DOWN -> providerLabel + " seems to be having problems right now. "
                    + "Try again shortly, or switch to another AI.";
            case NETWORK -> "Could not reach " + providerLabel + " — the server may have lost its "
                    + "internet connection.";
            case TIMEOUT -> providerLabel + " took too long to answer. Please try again.";
            case UNKNOWN -> providerLabel + " returned an unexpected error. Please try again.";
        };
    }

    /** The deepest useful raw message from the provider, tidied for display. */
    public static String providerReason(Throwable error) {
        String reason = null;
        for (Throwable t = error; t != null; t = next(t)) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                reason = t.getMessage();
            }
        }
        if (reason == null) {
            return null;
        }
        reason = reason.replaceAll("\\s+", " ").strip();
        return reason.length() <= 180 ? reason : reason.substring(0, 180) + "…";
    }

    private static Throwable next(Throwable t) {
        return t.getCause() == t ? null : t.getCause();
    }
}
