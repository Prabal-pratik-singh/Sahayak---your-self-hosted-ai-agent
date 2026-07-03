package com.sahayak.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.common.RateLimiter;
import com.sahayak.monitoring.ProviderException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public record ChatRequest(
            @NotBlank @Size(max = 8000) String message,
            @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]*") String conversationId,
            @Size(max = 20) @Pattern(regexp = "[a-z]*") String provider) {
    }

    public record ChatResponse(String reply) {
    }

    private final AgentService agentService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public ChatController(AgentService agentService, RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Validated @RequestBody ChatRequest request, Authentication authentication) {
        var user = checkedUser(authentication);
        return new ChatResponse(agentService.chat(
                user, conversationRef(request), request.message(), request.provider()));
    }

    /**
     * Streaming chat: the reply arrives token by token as Server-Sent Events —
     * "token" events carry a JSON-encoded string, then one "done" event.
     * Errors mid-stream become a single "error" event instead of a broken response.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Validated @RequestBody ChatRequest request,
                                                    Authentication authentication) {
        var user = checkedUser(authentication);
        return agentService.chatStream(user, conversationRef(request), request.message(), request.provider())
                // If the provider stalls (no token for this long), fail loudly
                // instead of leaving the user with an eternal spinner.
                .timeout(Duration.ofSeconds(120))
                .map(token -> ServerSentEvent.builder(json(token)).event("token").build())
                .concatWith(Flux.just(ServerSentEvent.builder("\"done\"").event("done").build()))
                .onErrorResume(e -> {
                    String message;
                    if (e instanceof ProviderException providerError) {
                        // already classified, counted and logged by ProviderHealthService
                        message = providerError.friendlyMessage();
                    } else if (e instanceof TimeoutException) {
                        log.warn("Streaming chat timed out waiting for the AI provider");
                        message = "The AI took too long to respond. Please try again.";
                    } else {
                        log.error("Streaming chat failed", e);
                        message = "The AI service returned an error. Check the server logs "
                                + "(commonly: an invalid API key or no credit for the selected AI provider).";
                    }
                    return Flux.just(ServerSentEvent.builder(json(message)).event("error").build());
                });
    }

    private AuthenticatedUser checkedUser(Authentication authentication) {
        var user = AuthenticatedUser.from(authentication);
        if (!rateLimiter.allow(user.id())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You are sending messages too quickly — wait a minute and try again.");
        }
        return user;
    }

    private static String conversationRef(ChatRequest request) {
        return (request.conversationId() == null || request.conversationId().isBlank())
                ? "default"
                : request.conversationId();
    }

    /** JSON-encodes a token so newlines survive the SSE wire format intact. */
    private String json(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
