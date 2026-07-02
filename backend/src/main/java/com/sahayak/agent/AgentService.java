package com.sahayak.agent;

import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.integrations.ConnectionService;
import com.sahayak.integrations.email.EmailService;
import com.sahayak.integrations.email.EmailTools;
import com.sahayak.integrations.linkedin.LinkedInService;
import com.sahayak.integrations.linkedin.LinkedInTools;
import com.sahayak.tasks.ScheduledTask;
import com.sahayak.tasks.ScheduledTaskRepository;
import com.sahayak.tasks.SchedulerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs one agent turn for one user. Every tool instance is created fresh and
 * bound to that user, so the model can only ever touch the caller's own
 * tasks, mailbox and LinkedIn — a hard boundary, not a prompt suggestion.
 */
@Service
public class AgentService {

    private final AiModelRegistry registry;
    private final ScheduledTaskRepository taskRepository;
    private final ConnectionService connectionService;
    private final EmailService emailService;
    private final LinkedInService linkedInService;

    public AgentService(AiModelRegistry registry,
                        ScheduledTaskRepository taskRepository,
                        ConnectionService connectionService,
                        EmailService emailService,
                        LinkedInService linkedInService) {
        this.registry = registry;
        this.taskRepository = taskRepository;
        this.connectionService = connectionService;
        this.emailService = emailService;
        this.linkedInService = linkedInService;
    }

    /** @param provider AI provider id ("anthropic", "openai", "gemini"); null/blank = server default */
    public String chat(AuthenticatedUser user, String conversationId, String message, String provider) {
        ChatClient client = registry.forChat(provider);
        return run(client, registry.resolveId(provider), user,
                "u" + user.id() + ":" + conversationId, message, false);
    }

    public String runScheduledTask(AuthenticatedUser user, ScheduledTask task) {
        ChatClient client = registry.forTask(task.getProvider());
        String message = "Automated run of scheduled task #%d. Execute this instruction now: %s"
                .formatted(task.getId(), task.getInstruction());
        return run(client, registry.resolveId(task.getProvider()), user,
                "u" + user.id() + ":task-" + task.getId(), message, true);
    }

    private String run(ChatClient client, String providerId, AuthenticatedUser user,
                       String conversationKey, String userMessage, boolean automatedRun) {
        List<Object> tools = new ArrayList<>();
        // The scheduler remembers which brain the user was talking to, so the
        // task later runs on that same provider.
        tools.add(new SchedulerTools(taskRepository, user.id(), providerId));
        connectionService.emailSettings(user.id())
                .ifPresent(settings -> tools.add(new EmailTools(emailService, settings)));
        connectionService.linkedInAccount(user.id())
                .ifPresent(account -> tools.add(new LinkedInTools(linkedInService, account)));

        return client.prompt()
                .system(Prompts.system(user, connectionService.promptSummary(user.id()), automatedRun))
                .user(userMessage)
                .tools(tools.toArray())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationKey))
                .call()
                .content();
    }
}
