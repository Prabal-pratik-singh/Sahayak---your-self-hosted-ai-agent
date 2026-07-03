package com.sahayak.conversations;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sidebar conversations: titles, pinning, message history and full-text
 * search. Message content itself is stored by Spring AI's chat memory; this
 * service owns the mapping between a user's conversation and its memory key,
 * which is also what keeps users from ever reading each other's chats.
 */
@Service
public class ConversationService {

    public record ConversationInfo(Long id, String title, boolean pinned, String createdAt, String updatedAt) {
        static ConversationInfo from(Conversation c) {
            return new ConversationInfo(c.getId(), c.getTitle(), c.isPinned(),
                    c.getCreatedAt().toString(), c.getUpdatedAt().toString());
        }
    }

    public record ChatMessage(String role, String content) {
    }

    public record SearchHit(Long conversationId, String title, String role, String snippet) {
    }

    private static final int MAX_CONVERSATIONS_PER_USER = 500;

    private final ConversationRepository repository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final JdbcTemplate jdbc;

    public ConversationService(ConversationRepository repository,
                               ChatMemoryRepository chatMemoryRepository,
                               JdbcTemplate jdbc) {
        this.repository = repository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.jdbc = jdbc;
    }

    public List<ConversationInfo> list(Long userId) {
        return repository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId).stream()
                .map(ConversationInfo::from)
                .toList();
    }

    public ConversationInfo create(Long userId, String title) {
        if (repository.countByUserId(userId) >= MAX_CONVERSATIONS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Conversation limit reached — delete some old chats first.");
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        if (title != null && !title.isBlank()) {
            conversation.setTitle(titleFrom(title));
        }
        return ConversationInfo.from(repository.save(conversation));
    }

    public ConversationInfo update(Long userId, Long id, String title, Boolean pinned) {
        Conversation conversation = owned(userId, id);
        if (title != null && !title.isBlank()) {
            conversation.setTitle(titleFrom(title));
        }
        if (pinned != null) {
            conversation.setPinned(pinned);
        }
        return ConversationInfo.from(repository.save(conversation));
    }

    public void delete(Long userId, Long id) {
        Conversation conversation = owned(userId, id);
        chatMemoryRepository.deleteByConversationId(memoryKey(userId, id));
        repository.delete(conversation);
    }

    public List<ChatMessage> messages(Long userId, Long id) {
        owned(userId, id);
        List<Message> stored = chatMemoryRepository.findByConversationId(memoryKey(userId, id));
        return stored.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> new ChatMessage(
                        m.getMessageType() == MessageType.USER ? "user" : "assistant",
                        m.getText()))
                .toList();
    }

    /** Case-insensitive text search across all of one user's conversations. */
    public List<SearchHit> search(Long userId, String query) {
        String like = "%" + escapeLike(query) + "%";
        return jdbc.query("""
                        SELECT c.id, c.title, m.type, m.content
                        FROM spring_ai_chat_memory m
                        JOIN conversations c
                          ON m.conversation_id = CONCAT('u', CAST(c.user_id AS text), ':c', CAST(c.id AS text))
                        WHERE c.user_id = ? AND m.content ILIKE ? ESCAPE '\\'
                        ORDER BY m."timestamp" DESC
                        LIMIT 20
                        """,
                (rs, i) -> new SearchHit(
                        rs.getLong(1),
                        rs.getString(2),
                        "USER".equals(rs.getString(3)) ? "user" : "assistant",
                        snippet(rs.getString(4), query)),
                userId, like);
    }

    /**
     * Turns the conversationId a client sent into the chat-memory key.
     * Numeric ids must belong to the caller (sidebar conversations, touched +
     * auto-titled here); anything else is a legacy/ephemeral thread that never
     * shows in the sidebar.
     */
    public String resolveMemoryKey(Long userId, String conversationRef, String firstMessage) {
        if (conversationRef != null && conversationRef.matches("\\d{1,18}")) {
            Conversation conversation = owned(userId, Long.parseLong(conversationRef));
            conversation.setUpdatedAt(LocalDateTime.now());
            if (Conversation.DEFAULT_TITLE.equals(conversation.getTitle()) && firstMessage != null) {
                conversation.setTitle(titleFrom(firstMessage));
            }
            repository.save(conversation);
            return memoryKey(userId, conversation.getId());
        }
        return "u" + userId + ":" + conversationRef;
    }

    private Conversation owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such conversation."));
    }

    static String memoryKey(Long userId, Long conversationId) {
        return "u" + userId + ":c" + conversationId;
    }

    /** First line of a message, trimmed to a sidebar-friendly title. */
    static String titleFrom(String text) {
        String firstLine = text.strip();
        int newline = firstLine.indexOf('\n');
        if (newline >= 0) {
            firstLine = firstLine.substring(0, newline);
        }
        String oneLine = firstLine.strip().replaceAll("\\s+", " ");
        if (oneLine.length() <= 48) {
            return oneLine.isEmpty() ? Conversation.DEFAULT_TITLE : oneLine;
        }
        int cut = oneLine.lastIndexOf(' ', 47);
        return oneLine.substring(0, cut > 24 ? cut : 47) + "…";
    }

    /** Escapes %, _ and \ so user input can't act as SQL LIKE wildcards. */
    static String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String snippet(String content, String query) {
        int at = content.toLowerCase().indexOf(query.toLowerCase());
        int start = Math.max(0, at - 40);
        int end = Math.min(content.length(), (at < 0 ? 0 : at) + query.length() + 80);
        String cut = content.substring(start, end).replaceAll("\\s+", " ").strip();
        return (start > 0 ? "…" : "") + cut + (end < content.length() ? "…" : "");
    }
}
