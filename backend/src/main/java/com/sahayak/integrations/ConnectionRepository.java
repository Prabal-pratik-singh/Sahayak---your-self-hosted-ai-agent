package com.sahayak.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    List<Connection> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<Connection> findByUserIdAndType(Long userId, Connection.Type type);

    Optional<Connection> findByIdAndUserId(Long id, Long userId);
}
