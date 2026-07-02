package com.sahayak.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Transactional
    void deleteByTokenHash(String tokenHash);

    @Transactional
    void deleteByExpiresAtBefore(LocalDateTime time);
}
