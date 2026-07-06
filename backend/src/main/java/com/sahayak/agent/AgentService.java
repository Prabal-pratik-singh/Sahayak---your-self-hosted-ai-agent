package com.sahayak.agent;

import com.sahayak.activity.ActivityService;
import com.sahayak.attachments.AttachmentService;
import com.sahayak.attachments.DocumentTextService;
import com.sahayak.attachments.StoredFile;
import com.sahayak.auth.AuthenticatedUser;
import com.sahayak.conversations.ConversationService;
import com.sahayak.integrations.ConnectionService;
import com.sahayak.integrations.email.EmailService;
import com.sahayak.integrations.email.EmailTools;
import com.sahayak.integrations.github.GitHubService;
import com.sahayak.integrations.github.GitHubTools;
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
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
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

    private final ProviderAccessService access;
    private final ScheduledTaskRepository taskRepository;
    private final AgentNoteRepository noteRepository;
    private final ConnectionService connectionService;
    private final ConversationService conversationService;
    private final EmailService emailService;
    private final LinkedInService linkedInService;
    private final GitHubService gitHubService;
    private final TelegramService telegramService;
    private final WebhookService webhookService;
    private final WebTools webTools;
    private final ProviderHealthService health;
    private final ActivityService activityService;
    private final AttachmentService attachmentService;
    private final DocumentTextService documentTextService;

    public AgentService(ProviderAccessService access,
                        ScheduledTaskRepository taskRepository,
                        AgentNoteRepository noteRepository,
                        ConnectionService connectionService,
                        ConversationService conversationService,
                        EmailService emailService,
                        LinkedInService linkedInService,
                        GitHubService gitHubService,
                        TelegramService telegramService,
                        WebhookService webhookService,
                        WebTools webTools,
                        ProviderHealthService health,
                        ActivityService activityService,
                        AttachmentService attachmentService,
                        DocumentTextService documentTextService) {
        this.access = access;
        this.taskRepository = taskRepository;
        this.noteRepository = noteRepository;
        this.connectionService = connectionService;
        this.conversationService = conversationService;
        this.emailService = emailService;
        this.linkedInService = linkedInService;
        this.gitHubService = gitHubService;
        this.telegramService = telegramService;
        this.webhookService = webhookService;
        this.webTools = webTools;
        this.health = health;
        this.activityService = activityService;
        this.attachmentService = attachmentService;
        this.documentTextService = documentTextService;
    }

    /** @param provider AI provider id ("anthropic", "openai", "gemini", "groq"); null/blank = default */
    public String chat(AuthenticatedUser user, String conversationRef, String message, String provider,
                       List<Long> attachmentIds) {
        List<StoredFile> files = attachmentService.fetchAll(user.id(), attachmentIds);
        var chosen = access.resolve(user.id(), provider);
        var resolved = routeForImages(user.id(), chosen, files);
        String note = visionNote(chosen, resolved);
        try {
            String reply = prepare(resolved, user, conversationRef, message, files).call().content();
            health.recordSuccess(resolved.healthScope());
            return note.isEmpty() ? reply : note + reply;
        } catch (RuntimeException e) {
            throw health.failure(resolved.healthScope(), resolved.label(), e);
        }
    }

    /** Streaming variant: emits the reply token by token. Every outcome is health-tracked. */
    public Flux<String> chatStream(AuthenticatedUser user, String conversationRef, String message, String provider,
                                   List<Long> attachmentIds) {
        List<StoredFile> files = attachmentService.fetchAll(user.id(), attachmentIds);
        var chosen = access.resolve(user.id(), provider);
        var resolved = routeForImages(user.id(), chosen, files);
        String note = visionNote(chosen, resolved);
        Flux<String> stream;
        try {
            stream = prepare(resolved, user, conversationRef, message, files).stream().content();
        } catch (RuntimeException e) {
            throw health.failure(resolved.healthScope(), resolved.label(), e);
        }
        if (!note.isEmpty()) {
            stream = Flux.just(note).concatWith(stream);
        }
        return stream
                .doOnComplete(() -> health.recordSuccess(resolved.healthScope()))
                .onErrorMap(e -> health.failure(resolved.healthScope(), resolved.label(), e));
    }

    /**
     * Text-only engines must never receive image input. When the message has
     * images and the chosen engine can't see, swap in a vision-capable engine
     * for this one turn (the user's own key wins, per BYOK).
     */
    private ProviderAccessService.ResolvedProvider routeForImages(Long userId,
                                                                  ProviderAccessService.ResolvedProvider chosen,
                                                                  List<StoredFile> files) {
        boolean hasImages = files.stream().anyMatch(f -> "image".equals(f.getKind()));
        if (!hasImages) {
            return chosen;
        }
        return access.visionFallback(userId, chosen).orElse(chosen);
    }

    /** Honest little heads-up when a different engine had to answer an image message. */
    private static String visionNote(ProviderAccessService.ResolvedProvider chosen,
                                     ProviderAccessService.ResolvedProvider used) {
        if (used == chosen) {
            return "";
        }
        return "_" + chosen.label() + " can't see images, so " + used.label() + " answered this one._\n\n";
    }

    public String runScheduledTask(AuthenticatedUser user, ScheduledTask task) {
        var resolved = access.resolveForTask(user.id(), task.getProvider());
        String message = "Automated run of scheduled task #%d. Execute this instruction now: %s"
                .formatted(task.getId(), task.getInstruction());
        try {
            String result = request(resolved, user,
                    "u" + user.id() + ":task-" + task.getId(), message, true, List.of()).call().content();
            health.recordSuccess(resolved.healthScope());
            return result;
        } catch (RuntimeException e) {
            // the friendly classified message becomes the task's stored result
            throw health.failure(resolved.healthScope(), resolved.label(), e);
        }
    }

    private ChatClient.ChatClientRequestSpec prepare(ProviderAccessService.ResolvedProvider resolved,
                                                     AuthenticatedUser user, String conversationRef, String message,
                                                     List<StoredFile> files) {
        String memoryKey = conversationService.resolveMemoryKey(user.id(), conversationRef, message);
        return request(resolved, user, memoryKey, message, false, files);
    }

    private ChatClient.ChatClientRequestSpec request(ProviderAccessService.ResolvedProvider resolved,
                                                     AuthenticatedUser user,
                                                     String memoryKey, String userMessage, boolean automatedRun,
                                                     List<StoredFile> files) {
        // Attached images go to the model as real image input (Spring AI Media),
        // never as a "file path" the model would have to pretend to open.
        List<Media> images = files.stream()
                .filter(f -> "image".equals(f.getKind()))
                .map(f -> new Media(MimeTypeUtils.parseMimeType(f.getMime()),
                        new ByteArrayResource(f.getContent())))
                .toList();
        String text = messageText(userMessage, files);

        var spec = resolved.client().prompt()
                .system(Prompts.system(user, connectionService.promptSummary(user.id()),
                        notesSummary(user.id()), automatedRun, resolved.toolCapable()))
                .user(u -> {
                    u.text(text);
                    if (!images.isEmpty()) {
                        u.media(images.toArray(Media[]::new));
                    }
                })
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey));

        // Chat-only engines (e.g. OpenRouter's free models) reject tool
        // definitions outright — so they get none, and the prompt tells the
        // model to be upfront about it.
        if (!resolved.toolCapable()) {
            return spec;
        }

        List<Object> tools = new ArrayList<>();
        // The scheduler remembers which brain the user was talking to, so the
        // task later runs on that same provider.
        tools.add(new SchedulerTools(taskRepository, user.id(), resolved.id()));
        tools.add(new MemoryTools(noteRepository, user.id()));
        tools.add(webTools);
        tools.add(new MessagingTools(telegramService, webhookService,
                connectionService.telegramConfig(user.id()),
                connectionService.webhookUrl(user.id(), Connection.Type.DISCORD),
                connectionService.webhookUrl(user.id(), Connection.Type.SLACK),
                activityService, user.id()));
        connectionService.emailSettings(user.id())
                .ifPresent(settings -> tools.add(new EmailTools(emailService, settings, activityService, user.id())));
        connectionService.linkedInAccount(user.id())
                .ifPresent(account -> tools.add(new LinkedInTools(linkedInService, account, activityService,
                        attachmentService, user.id())));
        connectionService.gitHubAccount(user.id())
                .ifPresent(account -> tools.add(new GitHubTools(gitHubService, account.accessToken(), activityService, user.id())));
        return spec.tools(tools.toArray());
    }

    /** Combined cap across ALL documents in one message (~12k tokens). */
    private static final int MAX_DOC_CHARS_TOTAL = 48_000;

    /**
     * The text actually sent to the model: fills in a default for
     * attachment-only sends, and inlines the extracted text of attached
     * documents (extracted server-side by Tika — the model never "opens"
     * files). Oversized content is truncated with an honest marker.
     */
    private String messageText(String userMessage, List<StoredFile> files) {
        StringBuilder text = new StringBuilder(
                (userMessage == null || userMessage.isBlank())
                        ? "Please look at the attached file(s) and respond to them."
                        : userMessage);
        // Attached images also get their storage ids listed, so the model can
        // pass them to postImageToLinkedIn — or embed them in a scheduleTask
        // instruction (storage outlives the chat, so a post firing days later
        // still finds its image).
        String imageIds = files.stream()
                .filter(f -> "image".equals(f.getKind()))
                .map(f -> "attachment id #" + f.getId() + " (" + f.getFilename() + ")")
                .collect(Collectors.joining(", "));
        if (!imageIds.isEmpty()) {
            text.append("\n\n[Attached image(s), also sent to you visually: ").append(imageIds).append(".]");
        }
        int budget = MAX_DOC_CHARS_TOTAL;
        for (StoredFile file : files) {
            if (!"doc".equals(file.getKind())) {
                continue;
            }
            String extracted = budget > 0 ? documentTextService.extract(file) : null;
            if (budget <= 0) {
                text.append("\n\n[Document \"").append(file.getFilename())
                        .append("\" was attached too, but the combined size limit was reached — ")
                        .append("tell the user to send it in a separate message.]");
            } else if (extracted == null) {
                text.append("\n\n[Document \"").append(file.getFilename())
                        .append("\" was attached but its text could not be extracted ")
                        .append("(it may be scanned, corrupt or password-protected). Be upfront about that.]");
            } else {
                if (extracted.length() > budget) {
                    extracted = extracted.substring(0, budget)
                            + "\n[… truncated: combined document size limit reached]";
                }
                budget -= extracted.length();
                // "already extracted / don't fetch" is load-bearing: without it,
                // smaller models see a filename and try to "open" it with a
                // web tool (observed with llama on Groq), which hard-fails.
                text.append("\n\n--- Attached document \"").append(file.getFilename())
                        .append("\" — its full text is ALREADY extracted below. ")
                        .append("Do NOT call any tool to open, fetch or read this file; just use this text. ---\n")
                        .append(extracted)
                        .append("\n--- End of \"").append(file.getFilename()).append("\" ---");
            }
        }
        return text.toString();
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
