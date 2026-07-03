package com.sahayak.agent;

import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.conversations.ConversationService;
import com.sahayak.integrations.ConnectionService;
import com.sahayak.integrations.email.EmailService;
import com.sahayak.integrations.email.EmailTools;
import com.sahayak.integrations.linkedin.LinkedInService;
import com.sahayak.integrations.linkedin.LinkedInTools;
import com.sahayak.integrations.messaging.MessagingTools;
import com.sahayak.integrations.messaging.TelegramService;
import com.sahayak.integrations.messaging.WebhookService;
import com.sahayak.integrations.Connection;
import com.sahayak.monitoring.ProviderHealthService;
import com.sahayak.notes.AgentNote;
import com.sahayak.notes.AgentNoteRepository;
import com.sahayak.notes.MemoryTools;
import com.sahayak.tasks.ScheduledTask;
import com.sahayak.tasks.ScheduledTaskRepository;
import com.sahayak.tasks.SchedulerTools;
import com.sahayak.web.WebTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs one agent turn for one user. Every user-scoped tool instance is
 * created fresh and bound to that user, so the model can only ever touch the
 * caller's own tasks, notes, mailbox and channels — a hard boundary, not a
 * prompt suggestion. WebTools is shared: it holds no user data.
 */
@Service
public class AgentService {

    private final AiModelRegistry registry;
    private final ScheduledTaskRepository taskRepository;
    private final AgentNoteRepository noteRepository;
    private final ConnectionService connectionService;
    private final ConversationService conversationService;
    private final EmailService emailService;
    private final LinkedInService linkedInService;
    private final TelegramService telegramService;
    private final WebhookService webhookService;
    private final WebTools webTools;
    private final ProviderHealthService health;

    public AgentService(AiModelRegistry registry,
                        ScheduledTaskRepository taskRepository,
                        AgentNoteRepository noteRepository,
                        ConnectionService connectionService,
                        ConversationService conversationService,
                        EmailService emailService,
                        LinkedInService linkedInService,
                        TelegramService telegramService,
                        WebhookService webhookService,
                        WebTools webTools,
                        ProviderHealthService health) {
        this.registry = registry;
        this.taskRepository = taskRepository;
        this.noteRepository = noteRepository;
        this.connectionService = connectionService;
        this.conversationService = conversationService;
        this.emailService = emailService;
        this.linkedInService = linkedInService;
        this.telegramService = telegramService;
        this.webhookService = webhookService;
        this.webTools = webTools;
        this.health = health;
    }

    /** @param provider AI provider id ("anthropic", "openai", "gemini"); null/blank = server default */
    public String chat(AuthenticatedUser user, String conversationRef, String message, String provider) {
        String providerId = registry.resolveId(provider);
        try {
            String reply = prepare(user, conversationRef, message, provider).call().content();
            health.recordSuccess(providerId);
            return reply;
        } catch (RuntimeException e) {
            throw health.failure(providerId, registry.labelOf(providerId), e);
        }
    }

    /** Streaming variant: emits the reply token by token. Every outcome is health-tracked. */
    public Flux<String> chatStream(AuthenticatedUser user, String conversationRef, String message, String provider) {
        String providerId = registry.resolveId(provider);
        Flux<String> stream;
        try {
            stream = prepare(user, conversationRef, message, provider).stream().content();
        } catch (RuntimeException e) {
            throw health.failure(providerId, registry.labelOf(providerId), e);
        }
        return stream
                .doOnComplete(() -> health.recordSuccess(providerId))
                .onErrorMap(e -> health.failure(providerId, registry.labelOf(providerId), e));
    }

    public String runScheduledTask(AuthenticatedUser user, ScheduledTask task) {
        String providerId = registry.resolveId(task.getProvider());
        ChatClient client = registry.forTask(task.getProvider());
        String message = "Automated run of scheduled task #%d. Execute this instruction now: %s"
                .formatted(task.getId(), task.getInstruction());
        try {
            String result = request(client, providerId, user,
                    "u" + user.id() + ":task-" + task.getId(), message, true).call().content();
            health.recordSuccess(providerId);
            return result;
        } catch (RuntimeException e) {
            // the friendly classified message becomes the task's stored result
            throw health.failure(providerId, registry.labelOf(providerId), e);
        }
    }

    private ChatClient.ChatClientRequestSpec prepare(AuthenticatedUser user, String conversationRef,
                                                     String message, String provider) {
        ChatClient client = registry.forChat(provider);
        String memoryKey = conversationService.resolveMemoryKey(user.id(), conversationRef, message);
        return request(client, registry.resolveId(provider), user, memoryKey, message, false);
    }

    private ChatClient.ChatClientRequestSpec request(ChatClient client, String providerId, AuthenticatedUser user,
                                                     String memoryKey, String userMessage, boolean automatedRun) {
        List<Object> tools = new ArrayList<>();
        // The scheduler remembers which brain the user was talking to, so the
        // task later runs on that same provider.
        tools.add(new SchedulerTools(taskRepository, user.id(), providerId));
        tools.add(new MemoryTools(noteRepository, user.id()));
        tools.add(webTools);
        tools.add(new MessagingTools(telegramService, webhookService,
                connectionService.telegramConfig(user.id()),
                connectionService.webhookUrl(user.id(), Connection.Type.DISCORD),
                connectionService.webhookUrl(user.id(), Connection.Type.SLACK)));
        connectionService.emailSettings(user.id())
                .ifPresent(settings -> tools.add(new EmailTools(emailService, settings)));
        connectionService.linkedInAccount(user.id())
                .ifPresent(account -> tools.add(new LinkedInTools(linkedInService, account)));

        return client.prompt()
                .system(Prompts.system(user, connectionService.promptSummary(user.id()),
                        notesSummary(user.id()), automatedRun))
                .user(userMessage)
                .tools(tools.toArray())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey));
    }

    private String notesSummary(Long userId) {
        List<AgentNote> recent = noteRepository.findTop15ByUserIdOrderByCreatedAtDesc(userId);
        if (recent.isEmpty()) {
            return "(none yet)";
        }
        String summary = recent.stream()
                .map(note -> "- " + note.getContent())
                .collect(Collectors.joining("\n"));
        long total = noteRepository.countByUserId(userId);
        if (total > recent.size()) {
            summary += "\n(" + (total - recent.size()) + " older notes exist — call listNotes to see all)";
        }
        return summary;
    }
}
