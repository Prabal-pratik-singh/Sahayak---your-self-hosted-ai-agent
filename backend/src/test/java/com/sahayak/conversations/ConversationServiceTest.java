package com.sahayak.conversations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationServiceTest {

    @Test
    void titleUsesFirstLineTrimmed() {
        assertEquals("What's the weather in Delhi?",
                ConversationService.titleFrom("  What's the weather in Delhi?  "));
        assertEquals("Line one", ConversationService.titleFrom("Line one\nline two\nline three"));
    }

    @Test
    void longTitlesAreCutAtAWordBoundary() {
        String title = ConversationService.titleFrom(
                "Please draft a very long LinkedIn post about our upcoming product launch next week");
        assertTrue(title.length() <= 49, title);
        assertTrue(title.endsWith("…"), title);
    }

    @Test
    void emptyMessageKeepsDefaultTitle() {
        assertEquals(Conversation.DEFAULT_TITLE, ConversationService.titleFrom("   "));
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertEquals("100\\% sure\\_thing\\\\x", ConversationService.escapeLike("100% sure_thing\\x"));
    }

    @Test
    void memoryKeyIsNamespacedPerUser() {
        assertEquals("u4:c12", ConversationService.memoryKey(4L, 12L));
    }
}
