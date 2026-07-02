package com.sahayak.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthTokenRepository tokenRepository = mock(AuthTokenRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthService service = new AuthService(userRepository, tokenRepository, encoder, true);

    @Test
    void registerStoresHashedPasswordAndIssuesToken() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        var result = service.register("Abhishek", "ME@Example.com", "secret-pass-123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertEquals("me@example.com", saved.getEmail());
        assertNotEquals("secret-pass-123", saved.getPasswordHash());
        assertTrue(encoder.matches("secret-pass-123", saved.getPasswordHash()));

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertEquals(AuthService.hash(result.token()), tokenCaptor.getValue().getTokenHash());
        assertEquals(1L, tokenCaptor.getValue().getUserId());
        assertFalse(result.token().isBlank());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);

        var e = assertThrows(ResponseStatusException.class,
                () -> service.register("A", "a@b.com", "secret-pass-123"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setEmail("a@b.com");
        user.setPasswordHash(encoder.encode("right-password"));
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));

        var e = assertThrows(ResponseStatusException.class,
                () -> service.login("a@b.com", "wrong-password"));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void loginIssuesTokenForCorrectPassword() {
        User user = new User();
        user.setId(1L);
        user.setEmail("a@b.com");
        user.setPasswordHash(encoder.encode("right-password"));
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));

        var result = service.login("a@b.com", "right-password");

        assertFalse(result.token().isBlank());
        assertEquals(1L, result.user().getId());
    }
}
