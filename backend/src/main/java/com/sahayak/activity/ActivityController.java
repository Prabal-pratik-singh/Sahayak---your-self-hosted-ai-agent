package com.sahayak.activity;

import com.sahayak.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The current user's recent real actions, for the Latest activity feed. */
@RestController
@RequestMapping("/api")
public class ActivityController {

    public record ActivityView(Long id, String kind, String text, String createdAt) {
        static ActivityView from(ActivityEvent e) {
            return new ActivityView(e.getId(), e.getKind(), e.getText(),
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        }
    }

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/activity")
    public List<ActivityView> activity(Authentication auth) {
        Long userId = AuthenticatedUser.from(auth).id();
        return activityService.recent(userId).stream().map(ActivityView::from).toList();
    }
}
