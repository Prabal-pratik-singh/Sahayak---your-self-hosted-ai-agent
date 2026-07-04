package com.sahayak.keys;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAiKeyRepository extends JpaRepository<UserAiKey, Long> {

    Optional<UserAiKey> findByUserIdAndProvider(Long userId, String provider);

    List<UserAiKey> findByUserId(Long userId);

    boolean existsByUserIdAndProvider(Long userId, String provider);
}
