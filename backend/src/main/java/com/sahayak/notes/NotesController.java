package com.sahayak.notes;

import com.sahayak.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The user-facing view of the agent's long-term memory: the same notes the AI
 * saves with "remember…" and reads on every message. Lets the user see, add,
 * and delete them from the Notes page. Everything is scoped to the caller.
 */
@RestController
@RequestMapping("/api/notes")
@Validated
public class NotesController {

    public record NoteView(Long id, String content, String createdAt) {
        static NoteView from(AgentNote n) {
            return new NoteView(n.getId(), n.getContent(),
                    n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        }
    }

    public record NewNote(@NotBlank @Size(max = 500) String content) {
    }

    private static final int MAX_NOTES = 200;

    private final AgentNoteRepository repository;

    public NotesController(AgentNoteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<NoteView> list(Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NoteView::from)
                .toList();
    }

    @PostMapping
    public NoteView add(@Validated @RequestBody NewNote request, Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        if (repository.countByUserId(userId) >= MAX_NOTES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You've reached the " + MAX_NOTES + "-note limit — delete a few first.");
        }
        AgentNote note = new AgentNote();
        note.setUserId(userId);
        note.setContent(request.content().strip());
        return NoteView.from(repository.save(note));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        repository.findByIdAndUserId(id, userId).ifPresent(repository::delete);
        return ResponseEntity.noContent().build();
    }
}
