package com.sahayak.tasks;

import com.sahayak.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TasksController {

    private final ScheduledTaskRepository repository;

    public TasksController(ScheduledTaskRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/tasks")
    public List<TaskInfo> tasks(Authentication authentication) {
        Long userId = AuthenticatedUser.from(authentication).id();
        return repository.findByUserIdOrderByRunAtDesc(userId).stream()
                .map(TaskInfo::from)
                .toList();
    }

    @DeleteMapping("/tasks/{id}")
    public TaskInfo cancel(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthenticatedUser.from(authentication).id();
        ScheduledTask task = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No task " + id));

        if (task.getStatus() == ScheduledTask.Status.PENDING) {
            task.setStatus(ScheduledTask.Status.CANCELLED);
            repository.save(task);
        }
        return TaskInfo.from(task);
    }
}
