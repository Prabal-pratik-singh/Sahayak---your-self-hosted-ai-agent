package com.sahayak.tasks;

import com.sahayak.agent.AgentService;
import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.auth.User;
import com.sahayak.auth.UserRepository;
import com.sahayak.monitoring.AiErrorCategory;
import com.sahayak.monitoring.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every 30 seconds, claims PENDING tasks whose time has come and executes
 * them by handing the stored instruction back to the agent as the task's
 * owner (so it uses that user's email/LinkedIn connections).
 *
 * DB-backed, so scheduled tasks survive restarts. Claiming is an atomic
 * UPDATE, so even two server instances can't run the same task twice.
 */
@Component
public class TaskRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);
    private static final int MAX_RESULT_LENGTH = 7900;
    private static final Duration STUCK_AFTER = Duration.ofMinutes(15);
    // A TEMPORARY provider problem (daily quota, provider down) must not kill
    // a scheduled task: postpone and retry instead. 30 min × 48 = a full day
    // of retries, enough to ride out any daily-quota reset.
    private static final int RETRY_DELAY_MINUTES = 30;
    private static final int MAX_RETRIES = 48;

    private final ScheduledTaskRepository repository;
    private final UserRepository userRepository;
    private final AgentService agentService;

    public TaskRunner(ScheduledTaskRepository repository,
                      UserRepository userRepository,
                      AgentService agentService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.agentService = agentService;
    }

    /** A task can only be RUNNING at boot if a previous run died mid-execution. */
    @EventListener(ApplicationReadyEvent.class)
    public void failTasksInterruptedByRestart() {
        for (ScheduledTask task : repository.findByStatus(ScheduledTask.Status.RUNNING)) {
            task.setStatus(ScheduledTask.Status.FAILED);
            task.setResult("Interrupted by a server restart before it could finish.");
            repository.save(task);
            log.warn("Task #{} was RUNNING at startup — marked FAILED", task.getId());
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void runDueTasks() {
        failStuckTasks();

        List<ScheduledTask> due = repository.findByStatusAndRunAtLessThanEqual(
                ScheduledTask.Status.PENDING, LocalDateTime.now());

        for (ScheduledTask task : due) {
            LocalDateTime startedAt = LocalDateTime.now();
            boolean claimed = repository.transition(task.getId(),
                    ScheduledTask.Status.PENDING, ScheduledTask.Status.RUNNING, startedAt) == 1;
            if (!claimed) {
                continue; // cancelled meanwhile, or another instance took it
            }
            // keep the local copy in sync with the claim we just wrote
            task.setStatus(ScheduledTask.Status.RUNNING);
            task.setStartedAt(startedAt);
            execute(task);
        }
    }

    private void execute(ScheduledTask task) {
        User owner = task.getUserId() != null
                ? userRepository.findById(task.getUserId()).orElse(null)
                : null;
        if (owner == null) {
            task.setStatus(ScheduledTask.Status.FAILED);
            task.setResult("This task has no owner account, so it cannot be executed.");
            repository.save(task);
            return;
        }

        log.info("Executing scheduled task #{} for {}: {}", task.getId(), owner.getEmail(), task.getInstruction());
        try {
            String result = agentService.runScheduledTask(AuthenticatedUser.from(owner), task);
            task.setStatus(ScheduledTask.Status.DONE);
            task.setResult(truncate(result));
            log.info("Task #{} done", task.getId());
        } catch (ProviderException e) {
            if (isTransient(e.category()) && task.getRetries() < MAX_RETRIES) {
                postpone(task, e);
            } else {
                log.error("Task #{} failed ({})", task.getId(), e.category(), e);
                task.setStatus(ScheduledTask.Status.FAILED);
                task.setResult(truncate("Error: " + e.friendlyMessage()));
            }
        } catch (Exception e) {
            log.error("Task #{} failed", task.getId(), e);
            task.setStatus(ScheduledTask.Status.FAILED);
            task.setResult(truncate("Error: " + e.getMessage()));
        }
        repository.save(task);
    }

    /**
     * The AI engine had a passing problem (quota exhausted, provider down…) —
     * the task itself is fine, so it goes back to PENDING a little later
     * instead of dying. Quota usually resets within hours.
     */
    private void postpone(ScheduledTask task, ProviderException e) {
        task.setRetries(task.getRetries() + 1);
        task.setStatus(ScheduledTask.Status.PENDING);
        task.setStartedAt(null);
        task.setRunAt(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
        task.setResult(truncate("Not run yet — " + e.friendlyMessage()
                + " Retrying automatically at " + task.getRunAt().toLocalTime().withSecond(0).withNano(0)
                + " (attempt " + task.getRetries() + " of " + MAX_RETRIES + ")."));
        log.warn("Task #{} hit a transient provider problem ({}) — postponed to {} (retry {}/{})",
                task.getId(), e.category(), task.getRunAt(), task.getRetries(), MAX_RETRIES);
    }

    /** Problems that heal on their own — retrying makes sense. A bad API key does not. */
    private static boolean isTransient(AiErrorCategory category) {
        return category == AiErrorCategory.QUOTA
                || category == AiErrorCategory.PROVIDER_DOWN
                || category == AiErrorCategory.NETWORK
                || category == AiErrorCategory.TIMEOUT;
    }

    /** Safety net for tasks claimed but never finished (e.g. by another, crashed instance). */
    private void failStuckTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minus(STUCK_AFTER);
        for (ScheduledTask task : repository.findByStatus(ScheduledTask.Status.RUNNING)) {
            if (task.getStartedAt() != null && task.getStartedAt().isBefore(cutoff)) {
                task.setStatus(ScheduledTask.Status.FAILED);
                task.setResult("Timed out: was still marked RUNNING after " + STUCK_AFTER.toMinutes() + " minutes.");
                repository.save(task);
                log.warn("Task #{} stuck in RUNNING — marked FAILED", task.getId());
            }
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_RESULT_LENGTH ? value : value.substring(0, MAX_RESULT_LENGTH) + "…";
    }
}
