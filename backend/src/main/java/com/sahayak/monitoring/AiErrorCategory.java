package com.sahayak.monitoring;

/** The ways an AI provider call goes wrong, in the user's terms. */
public enum AiErrorCategory {
    INVALID_KEY,       // 401/403 — wrong, expired, or under-permissioned API key
    QUOTA,             // 429 — rate limit or free-tier quota exhausted
    MODEL_UNAVAILABLE, // 404 / model decommissioned or renamed by the provider
    PROVIDER_DOWN,     // 5xx / "overloaded" — the provider itself is struggling
    NETWORK,           // DNS/connect failures — the server can't reach the provider
    TIMEOUT,           // no answer in time
    UNKNOWN
}
