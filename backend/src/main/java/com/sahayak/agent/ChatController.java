package com.sahayak.agent;

import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.common.RateLimiter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    public record ChatRequest(
            @NotBlank @Size(max = 8000) String message,
            @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]*") String conversationId,
            @Size(max = 20) @Pattern(regexp = "[a-z]*") String provider) {
    }

    public record ChatResponse(String reply) {
    }

    private final AgentService agentService;
    private final RateLimiter rateLimiter;

    public ChatController(AgentService agentService, RateLimiter rateLimiter) {
        this.agentService = agentService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Validated @RequestBody ChatRequest request, Authentication authentication) {
        var user = AuthenticatedUser.from(authentication);
        if (!rateLimiter.allow(user.id())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You are sending messages too quickly — wait a minute and try again.");
        }
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? "default"
                : request.conversationId();
        return new ChatResponse(agentService.chat(user, conversationId, request.message(), request.provider()));
    }
}
