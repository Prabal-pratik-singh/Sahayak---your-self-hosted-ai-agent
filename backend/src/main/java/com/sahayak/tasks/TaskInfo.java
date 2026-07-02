package com.sahayak.tasks;

public record TaskInfo(
        Long id,
        String instruction,
        String runAt,
        String status,
        String result,
        String provider,
        String createdAt) {

    public static TaskInfo from(ScheduledTask task) {
        return new TaskInfo(
                task.getId(),
                task.getInstruction(),
                task.getRunAt() != null ? task.getRunAt().toString() : null,
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getResult(),
                task.getProvider(),
                task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
    }
}
