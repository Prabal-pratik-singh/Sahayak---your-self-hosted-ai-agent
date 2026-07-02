package com.sahayak.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple per-user "N chat messages per minute" guard. Every chat message is a
 * paid Anthropic API call, so this caps a runaway script or an abusive user.
 * In-memory on purpose: Sahayak runs as a single instance.
 */
@Component
public class RateLimiter {

    private record Window(long minute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerMinute;

    public RateLimiter(@Value("${app.chat-rate-limit-per-minute:30}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public boolean allow(long userId) {
        long minute = System.currentTimeMillis() / 60_000;
        Window window = windows.compute(userId, (id, old) ->
                (old == null || old.minute() != minute) ? new Window(minute, new AtomicInteger()) : old);
        return window.count().incrementAndGet() <= limitPerMinute;
    }
}
