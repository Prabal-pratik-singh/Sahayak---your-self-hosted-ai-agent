package com.sahayak.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_tasks")
public class ScheduledTask {

    public enum Status {PENDING, RUNNING, DONE, FAILED, CANCELLED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nullable in the database only to tolerate rows created before user
     * accounts existed; the application always sets it.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * A complete, self-contained instruction the agent can execute later
     * without any conversation context, e.g.
     * "Post on LinkedIn with this exact caption: ..."
     */
    @Column(nullable = false, length = 4000)
    private String instruction;

    @Column(nullable = false)
    private LocalDateTime runAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(length = 8000)
    private String result;

    /** When execution actually began — used to detect tasks stuck in RUNNING. */
    private LocalDateTime startedAt;

    /**
     * How many times this task was postponed by a TEMPORARY provider problem
     * (quota exhausted, provider down). Bounded by TaskRunner so a task can't
     * retry forever. The SQL default keeps rows from before this column alive.
     */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int retries;

    /** AI provider this task was scheduled with ("anthropic", "openai", "gemini"). */
    @Column(length = 20)
    private String provider;

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

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public LocalDateTime getRunAt() {
        return runAt;
    }

    public void setRunAt(LocalDateTime runAt) {
        this.runAt = runAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
