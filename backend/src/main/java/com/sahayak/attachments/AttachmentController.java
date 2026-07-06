package com.sahayak.attachments;

import com.sahayak.attachments.AttachmentService.AttachmentRef;
import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.common.RateLimiter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Upload and retrieve chat attachments. Everything here is authenticated and
 * strictly owner-scoped — a user can only read files they uploaded.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final RateLimiter rateLimiter;

    public AttachmentController(AttachmentService attachmentService, RateLimiter rateLimiter) {
        this.attachmentService = attachmentService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public AttachmentRef upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "conversationId", required = false) Long conversationId,
                                Authentication authentication) {
        Long userId = AuthenticatedUser.from(authentication).id();
        if (!rateLimiter.allow(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You're uploading too quickly — wait a minute and try again.");
        }
        return attachmentService.store(userId, conversationId, file);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthenticatedUser.from(authentication).id();
        StoredFile stored = attachmentService.fetch(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such attachment."));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.getMime()))
                .contentLength(stored.getSizeBytes())
                // Uploaded files never change, and are private to the user.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + stored.getFilename().replace("\"", "") + "\"")
                .body(new ByteArrayResource(stored.getContent()));
    }
}
