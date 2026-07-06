package com.sahayak.integrations.linkedin;

import com.sahayak.activity.ActivityService;
import com.sahayak.attachments.AttachmentService;
import com.sahayak.attachments.StoredFile;
import com.sahayak.integrations.ConnectionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * The LinkedIn tools handed to the LLM — one instance per request, bound to
 * the current user's own LinkedIn account.
 */
public class LinkedInTools {

    private final LinkedInService linkedInService;
    private final ConnectionService.LinkedInAccount account;
    private final ActivityService activity;
    private final AttachmentService attachments;
    private final Long userId;

    public LinkedInTools(LinkedInService linkedInService, ConnectionService.LinkedInAccount account,
                         ActivityService activity, AttachmentService attachments, Long userId) {
        this.linkedInService = linkedInService;
        this.account = account;
        this.activity = activity;
        this.attachments = attachments;
        this.userId = userId;
    }

    @Tool(description = """
            Publish a public text post on the user's own LinkedIn profile. \
            Only call this after the user has confirmed the exact final text \
            in this conversation, or when executing an already-approved scheduled task.""")
    public String postToLinkedIn(
            @ToolParam(description = "The complete text of the LinkedIn post") String text) {
        if (account.expired()) {
            return "ERROR: the LinkedIn connection has expired. Ask the user to reconnect LinkedIn in the Connections panel.";
        }
        try {
            String url = linkedInService.post(account.accessToken(), account.personSub(), text);
            activity.record(userId, "linkedin", "Posted on LinkedIn");
            return "Posted on LinkedIn: " + url;
        } catch (RestClientResponseException e) {
            return "ERROR: LinkedIn refused the post (" + e.getStatusCode() + "): " + shorten(e.getResponseBodyAsString());
        } catch (Exception e) {
            return "ERROR: could not post on LinkedIn: " + e.getMessage();
        }
    }

    @Tool(description = """
            Publish a post with one or MORE images (up to 20) on the user's own LinkedIn \
            profile. The images must be files the user uploaded in this app — pass their \
            numeric attachment ids (shown in the conversation as "attachment id #N"). \
            Only call this after the user has confirmed the exact final caption and image(s) \
            in this conversation, or when executing an already-approved scheduled task.""")
    public String postImageToLinkedIn(
            @ToolParam(description = "Attachment ids of the uploaded images, in the order they should appear, e.g. [42] or [42, 43, 44]")
            List<Long> attachmentIds,
            @ToolParam(description = "The complete caption text of the LinkedIn post") String caption) {
        if (account.expired()) {
            return "ERROR: the LinkedIn connection has expired. Ask the user to reconnect LinkedIn in the Connections panel.";
        }
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return "ERROR: no attachment ids given — ask the user to attach the image(s) with the paperclip.";
        }
        if (attachmentIds.size() > 20) {
            return "ERROR: LinkedIn allows at most 20 images in one post; you passed " + attachmentIds.size() + ".";
        }

        List<LinkedInService.ImagePayload> images = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Long id : attachmentIds) {
            StoredFile file = attachments.fetch(userId, id).orElse(null);
            if (file == null) {
                return "ERROR: no attachment #" + id + " exists for this user. "
                        + "Ask the user to attach the image (paperclip) and try again.";
            }
            if (!"image".equals(file.getKind())) {
                return "ERROR: attachment #" + id + " (\"" + file.getFilename()
                        + "\") is a document, not an image — LinkedIn image posts need image files.";
            }
            images.add(new LinkedInService.ImagePayload(file.getContent(), file.getMime()));
            names.add(file.getFilename());
        }

        try {
            String url = linkedInService.postWithImages(account.accessToken(), account.personSub(), caption, images);
            activity.record(userId, "linkedin", images.size() == 1
                    ? "Posted an image on LinkedIn"
                    : "Posted " + images.size() + " images on LinkedIn");
            return "Posted on LinkedIn with " + (images.size() == 1 ? "image" : images.size() + " images")
                    + " (" + String.join(", ", names) + "): " + url;
        } catch (RestClientResponseException e) {
            return "ERROR: LinkedIn refused the image post (" + e.getStatusCode() + "): "
                    + shorten(e.getResponseBodyAsString());
        } catch (Exception e) {
            return "ERROR: could not post the image(s) on LinkedIn: " + e.getMessage();
        }
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
