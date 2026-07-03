package com.sahayak.notes;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * Long-term memory tools handed to the LLM — one instance per request, bound
 * to the current user, so notes can never leak between accounts.
 */
public class MemoryTools {

    private static final int MAX_NOTES_PER_USER = 100;
    private static final int MAX_NOTE_LENGTH = 500;

    private final AgentNoteRepository repository;
    private final Long userId;

    public MemoryTools(AgentNoteRepository repository, Long userId) {
        this.repository = repository;
        this.userId = userId;
    }

    @Tool(description = """
            Save a short permanent note about the user — preferences, stable facts, ongoing goals. \
            Use when the user says "remember ..." or shares something worth keeping long-term. \
            One clear sentence per note; do not save secrets or passwords.""")
    public String rememberNote(
            @ToolParam(description = "The fact to remember, as one short sentence") String content) {
        if (content == null || content.isBlank()) {
            return "ERROR: there is nothing to remember.";
        }
        if (repository.countByUserId(userId) >= MAX_NOTES_PER_USER) {
            return "ERROR: the note limit (" + MAX_NOTES_PER_USER + ") is reached. Ask the user which notes to forget.";
        }
        String trimmed = content.strip();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NOTE_LENGTH);
        }
        AgentNote note = new AgentNote();
        note.setUserId(userId);
        note.setContent(trimmed);
        note = repository.save(note);
        return "Noted (note #" + note.getId() + "): " + trimmed;
    }

    @Tool(description = "List all saved notes about the user, with their ids.")
    public String listNotes() {
        List<AgentNote> notes = repository.findByUserIdOrderByCreatedAtAsc(userId);
        if (notes.isEmpty()) {
            return "No saved notes yet.";
        }
        StringBuilder out = new StringBuilder("Saved notes:\n");
        for (AgentNote note : notes) {
            out.append("#").append(note.getId()).append(": ").append(note.getContent()).append('\n');
        }
        return out.toString();
    }

    @Tool(description = "Delete one saved note by its id — when it is outdated or the user asks to forget it.")
    public String forgetNote(
            @ToolParam(description = "The id of the note to delete") Long noteId) {
        return repository.findByIdAndUserId(noteId, userId)
                .map(note -> {
                    repository.delete(note);
                    return "Forgot note #" + noteId + " (\"" + note.getContent() + "\").";
                })
                .orElse("No note found with id " + noteId + ".");
    }
}
