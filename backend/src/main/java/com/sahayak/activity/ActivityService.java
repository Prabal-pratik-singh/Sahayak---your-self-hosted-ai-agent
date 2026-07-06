package com.sahayak.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Records real actions the agent performs. Recording must NEVER break the
 * action itself, so every failure here is swallowed and logged.
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityEventRepository repository;

    public ActivityService(ActivityEventRepository repository) {
        this.repository = repository;
    }

    public void record(Long userId, String kind, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return;
        }
        try {
            ActivityEvent event = new ActivityEvent();
            event.setUserId(userId);
            event.setKind(kind);
            event.setText(text.length() > 500 ? text.substring(0, 500) : text);
            repository.save(event);
        } catch (Exception e) {
            log.warn("Could not record activity for user {}: {}", userId, e.getMessage());
        }
    }

    public List<ActivityEvent> recent(Long userId) {
        return repository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }
}
