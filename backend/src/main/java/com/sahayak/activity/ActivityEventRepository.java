package com.sahayak.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    List<ActivityEvent> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
