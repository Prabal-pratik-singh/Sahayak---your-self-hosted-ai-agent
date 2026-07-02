package com.sahayak.tasks;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerToolsTest {

    private final ScheduledTaskRepository repository = mock(ScheduledTaskRepository.class);
    private final SchedulerTools tools = new SchedulerTools(repository, 42L, "anthropic");

    @Test
    void rejectsUnparseableDate() {
        String reply = tools.scheduleTask("say hi", "tomorrow evening");

        assertTrue(reply.startsWith("ERROR"), reply);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsTimeInThePast() {
        String reply = tools.scheduleTask("say hi", "2000-01-01T00:00:00");

        assertTrue(reply.startsWith("ERROR"), reply);
        verify(repository, never()).save(any());
    }

    @Test
    void savesTaskForTheCurrentUser() {
        when(repository.save(any())).thenAnswer(invocation -> {
            ScheduledTask task = invocation.getArgument(0);
            task.setId(7L);
            return task;
        });

        String reply = tools.scheduleTask("Send the report", "2999-01-01T10:00:00");

        ArgumentCaptor<ScheduledTask> captor = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(repository).save(captor.capture());
        ScheduledTask saved = captor.getValue();

        assertEquals(42L, saved.getUserId());
        assertEquals("anthropic", saved.getProvider());
        assertEquals("Send the report", saved.getInstruction());
        assertEquals(ScheduledTask.Status.PENDING, saved.getStatus());
        assertTrue(reply.contains("#7"), reply);
    }
}
