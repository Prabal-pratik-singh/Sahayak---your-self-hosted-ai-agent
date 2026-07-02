package com.sahayak.integrations;

import java.time.LocalDateTime;

/** What the API exposes about a connection — never the credentials themselves. */
public record ConnectionInfo(
        Long id,
        String type,
        String displayName,
        String status,
        String createdAt,
        String expiresAt) {

    public static ConnectionInfo from(Connection connection) {
        boolean expired = connection.getExpiresAt() != null
                && connection.getExpiresAt().isBefore(LocalDateTime.now());
        return new ConnectionInfo(
                connection.getId(),
                connection.getType().name(),
                connection.getDisplayName(),
                expired ? "EXPIRED" : "ACTIVE",
                connection.getCreatedAt() != null ? connection.getCreatedAt().toString() : null,
                connection.getExpiresAt() != null ? connection.getExpiresAt().toString() : null);
    }
}
