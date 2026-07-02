package com.sahayak.auth;

import org.springframework.security.core.Authentication;

/** The logged-in user, as seen by controllers and the agent. */
public record AuthenticatedUser(Long id, String email, String name) {

    public static AuthenticatedUser from(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
