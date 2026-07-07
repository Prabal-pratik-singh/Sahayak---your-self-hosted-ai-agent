package com.sahayak.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * One connected external account for one user (their mailbox, their LinkedIn).
 * The credentials live in {@code encryptedConfig} as an AES-encrypted JSON blob —
 * never in plain text.
 */
@Entity
@Table(name = "connections", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "type"}))
public class Connection {

    public enum Type {EMAIL, LINKEDIN, TELEGRAM, DISCORD, SLACK, GITHUB, GOOGLE_CALENDAR, GMAIL_COMPOSIO}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    /** What the user sees in the UI, e.g. "you@gmail.com" or the LinkedIn profile name. */
    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 8000)
    private String encryptedConfig;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** When the connection stops working (LinkedIn tokens last ~60 days). Null = does not expire. */
    private LocalDateTime expiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEncryptedConfig() {
        return encryptedConfig;
    }

    public void setEncryptedConfig(String encryptedConfig) {
        this.encryptedConfig = encryptedConfig;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
