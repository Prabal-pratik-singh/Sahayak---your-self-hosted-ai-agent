package com.sahayak.tasks;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Scheduler tools exposed to the LLM. This is how "post it tomorrow at 6 PM"
 * works: the model calls scheduleTask instead of acting immediately, and
 * TaskRunner executes the stored instruction when the time comes.
 *
 * One instance is created per request, bound to the current user, so the
 * model can only see and cancel that user's own tasks.
 */
public class SchedulerTools {

    private final ScheduledTaskRepository repository;
    private final Long userId;
    private final String provider;

    public SchedulerTools(ScheduledTaskRepository repository, Long userId, String provider) {
        this.repository = repository;
        this.userId = userId;
        this.provider = provider;
    }

    @Tool(description = """
            Schedule an instruction to be executed automatically at a future date-time (one-time, not recurring). \
            Use this whenever the user wants something done later, e.g. "tomorrow at 6 PM" or "in 2 hours". \
            The instruction must be complete and self-contained (include the full final content, e.g. the exact \
            LinkedIn caption or the full email body and recipient), because it will be executed later without \
            any conversation context.""")
    public String scheduleTask(
            @ToolParam(description = "Complete self-contained instruction, e.g. 'Post on LinkedIn with this exact caption: ...'")
            String instruction,
            @ToolParam(description = "When to run, ISO-8601 local date-time, e.g. 2026-07-03T18:00:00")
            String runAt) {

        LocalDateTime time;
        try {
            time = LocalDateTime.parse(runAt);
        } catch (DateTimeParseException e) {
            return "ERROR: runAt must be an ISO-8601 local date-time like 2026-07-03T18:00:00. You sent: " + runAt;
        }
        if (time.isBefore(LocalDateTime.now())) {
            return "ERROR: " + runAt + " is in the past. Current time is " + LocalDateTime.now() + ".";
        }

        ScheduledTask task = new ScheduledTask();
        task.setUserId(userId);
        task.setProvider(provider);
        task.setInstruction(instruction);
        task.setRunAt(time);
        task = repository.save(task);

        return "Scheduled as task #" + task.getId() + " for " + time
                + ". It will be executed automatically (checked every 30 seconds).";
    }

    @Tool(description = "List the user's scheduled tasks with id, instruction, run time, status (PENDING/RUNNING/DONE/FAILED/CANCELLED) and result.")
    public List<TaskInfo> listScheduledTasks() {
        return repository.findByUserIdOrderByRunAtDesc(userId).stream()
                .map(TaskInfo::from)
                .toList();
    }

    @Tool(description = "Cancel one of the user's PENDING scheduled tasks by its id.")
    public String cancelScheduledTask(
            @ToolParam(description = "The id of the task to cancel") Long taskId) {

        return repository.findByIdAndUserId(taskId, userId)
                .map(task -> {
                    if (task.getStatus() != ScheduledTask.Status.PENDING) {
                        return "Task #" + taskId + " is " + task.getStatus() + " and cannot be cancelled.";
                    }
                    task.setStatus(ScheduledTask.Status.CANCELLED);
                    repository.save(task);
                    return "Task #" + taskId + " cancelled.";
                })
                .orElse("No task found with id " + taskId + ".");
    }
}
