package com.sahayak.attachments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** Owner-scoped fetch — the only way callers read a file, so users can't read each other's. */
    Optional<StoredFile> findByIdAndUserId(Long id, Long userId);
}
