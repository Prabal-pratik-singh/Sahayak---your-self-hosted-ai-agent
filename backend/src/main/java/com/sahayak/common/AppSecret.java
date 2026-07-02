package com.sahayak.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The one secret the server needs: it signs nothing by itself, but every
 * login token hash and every encrypted connection (SMTP passwords, LinkedIn
 * tokens) is derived from it.
 *
 * Resolution order: the APP_SECRET environment variable, otherwise a random
 * secret generated once and kept in <home>/.sahayak/app-secret.txt so that
 * logins and stored connections survive restarts with zero configuration.
 */
@Component
public class AppSecret {

    private static final Logger log = LoggerFactory.getLogger(AppSecret.class);

    private final String value;

    public AppSecret(@Value("${app.secret:}") String configured) {
        this.value = (configured != null && !configured.isBlank())
                ? configured.trim()
                : loadOrCreateLocalSecret();
    }

    public String value() {
        return value;
    }

    private static String loadOrCreateLocalSecret() {
        Path file = Path.of(System.getProperty("user.home"), ".sahayak", "app-secret.txt");
        try {
            if (Files.exists(file)) {
                return Files.readString(file).trim();
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Files.createDirectories(file.getParent());
            Files.writeString(file, secret);
            log.warn("APP_SECRET is not set. Generated one and saved it to {} — set APP_SECRET yourself when deploying for real.", file);
            return secret;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read or create the app secret at " + file
                    + ". Set the APP_SECRET environment variable instead.", e);
        }
    }
}
