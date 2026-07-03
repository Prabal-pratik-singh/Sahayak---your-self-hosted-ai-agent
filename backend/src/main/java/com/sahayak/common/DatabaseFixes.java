package com.sahayak.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Small schema corrections that Hibernate does not manage.
 *
 * Spring AI creates the chat-history table with conversation_id varchar(36),
 * but our keys are "u<userId>:<36-char browser id>" (39+ chars). Without this
 * widening, EVERY chat message from the UI failed to save with a confusing
 * "already exists" error. Idempotent — safe to run on every start.
 */
@Component
public class DatabaseFixes {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFixes.class);

    private final JdbcTemplate jdbc;

    public DatabaseFixes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void widenConversationIdColumn() {
        try {
            jdbc.execute("ALTER TABLE spring_ai_chat_memory ALTER COLUMN conversation_id TYPE varchar(128)");
            log.info("Ensured spring_ai_chat_memory.conversation_id is varchar(128)");
        } catch (Exception e) {
            log.warn("Could not widen spring_ai_chat_memory.conversation_id — chat saving may fail: {}", e.getMessage());
        }
    }
}
