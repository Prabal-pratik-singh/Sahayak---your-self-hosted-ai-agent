package com.sahayak.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findByStatusAndRunAtLessThanEqual(ScheduledTask.Status status, LocalDateTime time);

    List<ScheduledTask> findByStatus(ScheduledTask.Status status);

    List<ScheduledTask> findByUserIdOrderByRunAtDesc(Long userId);

    Optional<ScheduledTask> findByIdAndUserId(Long id, Long userId);

    /**
     * Atomically claims a task (PENDING → RUNNING). Returns 1 only for the
     * claimer, so a task can never be executed twice even if two server
     * instances poll at the same moment.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update ScheduledTask t set t.status = :to, t.startedAt = :now where t.id = :id and t.status = :from")
    int transition(@Param("id") Long id,
                   @Param("from") ScheduledTask.Status from,
                   @Param("to") ScheduledTask.Status to,
                   @Param("now") LocalDateTime now);
}
