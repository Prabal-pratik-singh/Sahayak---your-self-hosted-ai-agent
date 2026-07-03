package com.sahayak.notes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentNoteRepository extends JpaRepository<AgentNote, Long> {

    List<AgentNote> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<AgentNote> findTop15ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<AgentNote> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
