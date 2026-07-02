package com.sahayak.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private static final Duration TOKEN_LIFETIME = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean allowSignups;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       AuthTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.allow-signups:true}") boolean allowSignups) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.allowSignups = allowSignups;
    }

    public record AuthResult(String token, User user) {
    }

    public AuthResult register(String name, String email, String password) {
        // The very first account can always be created, so the owner can never lock themselves out.
        if (!allowSignups && userRepository.count() > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signups are disabled on this server.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setDisplayName(name.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);
        return new AuthResult(issueToken(user), user);
    }

    public AuthResult login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password."));
        return new AuthResult(issueToken(user), user);
    }

    /** Returns the user a bearer token belongs to, if the token is valid and not expired. */
    public Optional<User> authenticate(String rawToken) {
        return tokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .flatMap(t -> userRepository.findById(t.getUserId()));
    }

    public void logout(String rawToken) {
        tokenRepository.deleteByTokenHash(hash(rawToken));
    }

    private String issueToken(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AuthToken record = new AuthToken();
        record.setTokenHash(hash(token));
        record.setUserId(user.getId());
        record.setExpiresAt(LocalDateTime.now().plus(TOKEN_LIFETIME));
        tokenRepository.save(record);
        return token;
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 60_000)
    public void purgeExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
