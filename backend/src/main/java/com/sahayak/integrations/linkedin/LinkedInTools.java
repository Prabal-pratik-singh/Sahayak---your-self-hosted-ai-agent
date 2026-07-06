package com.sahayak.integrations.linkedin;

import com.sahayak.activity.ActivityService;
import com.sahayak.integrations.ConnectionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientResponseException;

/**
 * The LinkedIn tool handed to the LLM — one instance per request, bound to the
 * current user's own LinkedIn account.
 */
public class LinkedInTools {

    private final LinkedInService linkedInService;
    private final ConnectionService.LinkedInAccount account;
    private final ActivityService activity;
    private final Long userId;

    public LinkedInTools(LinkedInService linkedInService, ConnectionService.LinkedInAccount account,
                         ActivityService activity, Long userId) {
        this.linkedInService = linkedInService;
        this.account = account;
        this.activity = activity;
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

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
