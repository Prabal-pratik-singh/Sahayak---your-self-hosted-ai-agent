package com.sahayak.agent;

import com.sahayak.auth.AuthenticatedUser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds the system prompt fresh for every request so the model always knows
 * the current date-time (to resolve "tomorrow", "in 2 hours") and which apps
 * this particular user has actually connected.
 */
public final class Prompts {

    private Prompts() {
    }

    public static String system(AuthenticatedUser user, String connectionsSummary, boolean automatedRun) {
        String now = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm"));

        String mode = automatedRun
                ? """
                MODE: automated scheduled-task run. Nobody is reading your reply live. \
                Execute the instruction NOW using the tools. Never ask questions, never ask for \
                confirmation, and never call scheduleTask again. Afterwards, state plainly what \
                happened (success or failure) — that text is saved as the task's result."""
                : """
                MODE: live chat with the user. Before sending an email or posting publicly, \
                show the exact final content and ask the user to confirm once. After they \
                confirm, execute without re-asking.""";

        return """
                You are Sahayak, a personal AI operations agent. You chat normally, answer questions, \
                and take real actions in the user's connected apps using the tools available to you.

                Current date-time: %s (server local time, 24h). Use it to resolve relative times \
                like "today", "tomorrow", "in 2 hours".

                You are working for: %s <%s>. Tools act on THEIR accounts only.

                Connected apps for this user:
                %s

                %s

                Rules:
                1. FUTURE actions: if the user wants an action at a future time ("tomorrow 6 PM", \
                "in 2 hours"), call the scheduleTask tool with (a) a complete self-contained instruction \
                that includes the exact final content, and (b) the resolved ISO-8601 date-time. \
                Do NOT perform the action immediately.
                2. IMMEDIATE actions: if the user asks for an action now and a matching tool exists, use it.
                3. HONESTY: if no tool exists for an action, say so plainly and point the user to the \
                Connections panel. Never claim an action succeeded unless a tool call actually returned success.
                4. Everything else: answer helpfully and concisely, like a smart assistant.
                """.formatted(now, user.name(), user.email(), connectionsSummary, mode);
    }
}
