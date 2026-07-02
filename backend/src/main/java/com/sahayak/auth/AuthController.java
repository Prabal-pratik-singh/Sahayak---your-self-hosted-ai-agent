package com.sahayak.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserView(Long id, String email, String name) {
        static UserView from(User user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName());
        }
    }

    public record AuthResponse(String token, UserView user) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Validated @RequestBody RegisterRequest request) {
        var result = authService.register(request.name(), request.email(), request.password());
        return new AuthResponse(result.token(), UserView.from(result.user()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Validated @RequestBody LoginRequest request) {
        var result = authService.login(request.email(), request.password());
        return new AuthResponse(result.token(), UserView.from(result.user()));
    }

    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        var user = AuthenticatedUser.from(authentication);
        return new UserView(user.id(), user.email(), user.name());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object rawToken = request.getAttribute(TokenAuthFilter.RAW_TOKEN_ATTRIBUTE);
        if (rawToken instanceof String token) {
            authService.logout(token);
        }
        return ResponseEntity.noContent().build();
    }
}
