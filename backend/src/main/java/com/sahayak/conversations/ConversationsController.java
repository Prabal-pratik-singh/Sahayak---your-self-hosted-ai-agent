package com.sahayak.conversations;

import com.sahayak.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@Validated
public class ConversationsController {

    public record CreateRequest(@Size(max = 200) String title) {
    }

    public record UpdateRequest(@Size(max = 200) String title, Boolean pinned) {
    }

    private final ConversationService service;

    public ConversationsController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConversationService.ConversationInfo> list(Authentication auth) {
        return service.list(AuthenticatedUser.from(auth).id());
    }

    @PostMapping
    public ConversationService.ConversationInfo create(@RequestBody(required = false) CreateRequest request,
                                                       Authentication auth) {
        return service.create(AuthenticatedUser.from(auth).id(),
                request != null ? request.title() : null);
    }

    @PatchMapping("/{id}")
    public ConversationService.ConversationInfo update(@PathVariable Long id,
                                                       @Validated @RequestBody UpdateRequest request,
                                                       Authentication auth) {
        return service.update(AuthenticatedUser.from(auth).id(), id, request.title(), request.pinned());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(AuthenticatedUser.from(auth).id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public List<ConversationService.ChatMessage> messages(@PathVariable Long id, Authentication auth) {
        return service.messages(AuthenticatedUser.from(auth).id(), id);
    }

    @GetMapping("/search")
    public List<ConversationService.SearchHit> search(@RequestParam @NotBlank @Size(max = 100) String q,
                                                      Authentication auth) {
        return service.search(AuthenticatedUser.from(auth).id(), q.strip());
    }
}
