package com.sahayak.contact;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public contact form. Messages land in the contact_messages table; a small
 * per-IP limit keeps drive-by spam from filling the database.
 */
@RestController
@RequestMapping("/api/contact")
@Validated
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private static final int MAX_PER_HOUR_PER_IP = 5;

    public record ContactRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 10, max = 2000) String message) {
    }

    private record Window(long hour, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ContactMessageRepository repository;

    public ContactController(ContactMessageRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@Validated @RequestBody ContactRequest request,
                                                      HttpServletRequest http) {
        String ip = clientIp(http);
        long hour = System.currentTimeMillis() / 3_600_000;
        Window window = windows.compute(ip, (k, old) ->
                (old == null || old.hour() != hour) ? new Window(hour, new AtomicInteger()) : old);
        if (window.count().incrementAndGet() > MAX_PER_HOUR_PER_IP) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many messages from this address — please try again later.");
        }

        ContactMessage saved = new ContactMessage();
        saved.setName(request.name().strip());
        saved.setEmail(request.email().strip());
        saved.setMessage(request.message().strip());
        repository.save(saved);
        log.info("Contact message #{} received from {} <{}>", saved.getId(), saved.getName(), saved.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "received"));
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].strip()
                : http.getRemoteAddr();
    }
}
