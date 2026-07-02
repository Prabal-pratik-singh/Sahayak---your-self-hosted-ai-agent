package com.sahayak.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into the same clean JSON shape the frontend can show,
 * instead of Spring's default HTML/stacktrace pages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("Invalid request.");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Request body is not valid JSON."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> statusException(ResponseStatusException e) {
        String reason = e.getReason() != null ? e.getReason() : "Request failed.";
        log.warn("Request rejected ({}): {}", e.getStatusCode(), reason);
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(reason));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException e) {
        log.warn("Database rejected the request as a duplicate/conflict", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("That already exists."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Not found."));
    }

    /** The Anthropic API (or another upstream HTTP service) failed. */
    @ExceptionHandler({
            NonTransientAiException.class,
            TransientAiException.class,
            RestClientResponseException.class,
            ResourceAccessException.class})
    public ResponseEntity<ErrorResponse> upstreamFailed(Exception e) {
        log.error("Upstream service call failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("The AI service returned an error. Check the server logs "
                        + "(commonly: an invalid API key or no credit for the selected AI provider)."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected server error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Something went wrong on the server."));
    }
}
