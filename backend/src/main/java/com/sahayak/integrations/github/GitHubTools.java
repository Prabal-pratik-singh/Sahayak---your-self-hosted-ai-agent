package com.sahayak.integrations.github;

import com.sahayak.activity.ActivityService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientResponseException;

/**
 * GitHub tools handed to the LLM — one instance per request, bound to the
 * current user's own GitHub account.
 */
public class GitHubTools {

    private final GitHubService gitHubService;
    private final String token;
    private final ActivityService activity;
    private final Long userId;

    public GitHubTools(GitHubService gitHubService, String token, ActivityService activity, Long userId) {
        this.gitHubService = gitHubService;
        this.token = token;
        this.activity = activity;
        this.userId = userId;
    }

    @Tool(description = """
            Create an issue in one of the user's GitHub repositories. \
            Only call after the user has confirmed the repository, title and body \
            in this conversation, or when executing an already-approved scheduled task.""")
    public String createGitHubIssue(
            @ToolParam(description = "Repository as 'owner/name', e.g. 'prabal/sahayak'") String repo,
            @ToolParam(description = "Issue title") String title,
            @ToolParam(description = "Issue body (Markdown allowed)") String body) {
        try {
            String url = gitHubService.createIssue(token, repo.trim(), title, body);
            activity.record(userId, "github", "Created a GitHub issue in " + repo.trim());
            return "Created GitHub issue: " + url;
        } catch (RestClientResponseException e) {
            return "ERROR: GitHub refused (" + e.getStatusCode() + "): " + shorten(e.getResponseBodyAsString());
        } catch (Exception e) {
            return "ERROR: could not create the issue: " + e.getMessage();
        }
    }

    @Tool(description = "List the user's most recently updated GitHub repositories (name + description).")
    public String listMyGitHubRepos() {
        try {
            GitHubService.Repo[] repos = gitHubService.listRepos(token);
            if (repos.length == 0) {
                return "No repositories found for this account.";
            }
            StringBuilder sb = new StringBuilder();
            for (GitHubService.Repo r : repos) {
                sb.append("- ").append(r.fullName());
                if (r.description() != null && !r.description().isBlank()) {
                    sb.append(" — ").append(r.description());
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "ERROR: could not list repositories: " + e.getMessage();
        }
    }

    @Tool(description = """
            Search GitHub issues and pull requests. Build a GitHub search query, \
            e.g. 'repo:owner/name is:open label:bug' or 'assignee:me is:issue'.""")
    public String searchGitHubIssues(
            @ToolParam(description = "GitHub issue-search query") String query) {
        try {
            return gitHubService.searchIssues(token, query);
        } catch (Exception e) {
            return "ERROR: could not search GitHub: " + e.getMessage();
        }
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
