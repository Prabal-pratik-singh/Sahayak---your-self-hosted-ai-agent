package com.sahayak.notes;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryToolsTest {

    private final AgentNoteRepository repository = mock(AgentNoteRepository.class);
    private final MemoryTools tools = new MemoryTools(repository, 42L);

    @Test
    void rejectsEmptyNotes() {
        assertTrue(tools.rememberNote("   ").startsWith("ERROR"));
        verify(repository, never()).save(any());
    }

    @Test
    void savesNoteForTheCurrentUser() {
        when(repository.countByUserId(42L)).thenReturn(3L);
        when(repository.save(any())).thenAnswer(invocation -> {
            AgentNote note = invocation.getArgument(0);
            note.setId(9L);
            return note;
        });

        String reply = tools.rememberNote("Prefers short replies");

        ArgumentCaptor<AgentNote> captor = ArgumentCaptor.forClass(AgentNote.class);
        verify(repository).save(captor.capture());
        assertEquals(42L, captor.getValue().getUserId());
        assertEquals("Prefers short replies", captor.getValue().getContent());
        assertTrue(reply.contains("#9"), reply);
    }

    @Test
    void refusesWhenNoteLimitReached() {
        when(repository.countByUserId(42L)).thenReturn(100L);
        assertTrue(tools.rememberNote("one more").startsWith("ERROR"));
        verify(repository, never()).save(any());
    }

    @Test
    void forgetOnlyTouchesOwnNotes() {
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());
        assertEquals("No note found with id 7.", tools.forgetNote(7L));
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(anyLong());
    }
}
