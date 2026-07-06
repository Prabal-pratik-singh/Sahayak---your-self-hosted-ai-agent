package com.sahayak.attachments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A file a user uploaded in chat (image or document), stored as bytes in
 * Postgres so it survives restarts and container rebuilds.
 *
 * Storage is deliberately INDEPENDENT of the conversation lifecycle: a
 * scheduled LinkedIn image post may fire hours or days later and must still be
 * able to fetch its image, even if the originating chat was deleted. The
 * {@code conversationId} is a soft association only — never a foreign key that
 * would cascade-delete the bytes.
 */
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Soft link to the chat it was uploaded in; null is allowed. */
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 100)
    private String mime;

    /** "image" or "doc" — mirrors the client's classification, for icon + routing. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // No @Lob: on Postgres a plain byte[] maps to bytea (inline), whereas @Lob
    // would map to a large-object OID needing separate stream handling.
    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
