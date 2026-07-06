package com.sahayak.integrations.google;

import com.sahayak.activity.ActivityService;
import com.sahayak.integrations.ConnectionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Google Calendar tools handed to the LLM — one instance per request, bound to
 * the current user's own Google account.
 *
 * Google access tokens last ~1 hour, so before every call we check the stored
 * expiry and silently mint a fresh token from the refresh token when needed —
 * the user connects once and never thinks about it again.
 */
public class GoogleCalendarTools {

    private final GoogleCalendarService calendarService;
    private final ConnectionService connectionService;
    private final ActivityService activity;
    private final Long userId;

    /** Snapshot from the DB at request start; replaced in-place after a refresh. */
    private ConnectionService.GoogleCalendarAccount account;

    public GoogleCalendarTools(GoogleCalendarService calendarService,
                               ConnectionService connectionService,
                               ActivityService activity,
                               Long userId,
                               ConnectionService.GoogleCalendarAccount account) {
        this.calendarService = calendarService;
        this.connectionService = connectionService;
        this.activity = activity;
        this.userId = userId;
        this.account = account;
    }

    @Tool(description = """
            Create an event on the user's Google Calendar (primary calendar). \
            Times are local date-times in the user's timezone. If the user didn't \
            give an end time, use start + 1 hour. \
            Only call after the user confirmed title and time in this conversation, \
            or when executing an already-approved scheduled task.""")
    public String createCalendarEvent(
            @ToolParam(description = "Event title, e.g. 'Dentist appointment'") String title,
            @ToolParam(description = "Start, ISO-8601 local date-time, e.g. 2026-07-08T15:00:00") String startDateTime,
            @ToolParam(description = "End, ISO-8601 local date-time (start + 1 hour if the user gave none)") String endDateTime,
            @ToolParam(description = "Optional longer description; empty string if none") String description) {
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = LocalDateTime.parse(startDateTime);
            end = LocalDateTime.parse(endDateTime);
        } catch (DateTimeParseException e) {
            return "ERROR: start/end must be ISO-8601 local date-times like 2026-07-08T15:00:00. You sent: "
                    + startDateTime + " / " + endDateTime;
        }
        if (!end.isAfter(start)) {
            return "ERROR: the end time must be after the start time.";
        }
        try {
            String link = calendarService.createEvent(freshToken(), title, description,
                    startDateTime, endDateTime);
            activity.record(userId, "calendar", "Created calendar event: " + title);
            return "Calendar event created: " + link;
        } catch (RestClientResponseException e) {
            return refused(e);
        } catch (Exception e) {
            return "ERROR: could not create the event: " + e.getMessage();
        }
    }

    @Tool(description = "List the user's upcoming Google Calendar events for the next N days (primary calendar).")
    public String listUpcomingCalendarEvents(
            @ToolParam(description = "How many days ahead to look, 1-60") Integer days) {
        int window = days == null ? 7 : Math.max(1, Math.min(60, days));
        try {
            String events = calendarService.listUpcoming(freshToken(), window);
            return events.isEmpty()
                    ? "No events on the calendar in the next " + window + " day(s)."
                    : events;
        } catch (RestClientResponseException e) {
            return refused(e);
        } catch (Exception e) {
            return "ERROR: could not read the calendar: " + e.getMessage();
        }
    }

    /** A valid access token — refreshed and re-stored if the cached one is stale. */
    private String freshToken() {
        if (!account.accessExpired()) {
            return account.accessToken();
        }
        GoogleCalendarService.Token token = calendarService.refresh(account.refreshToken());
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IllegalStateException(
                    "Google rejected the stored refresh token — ask the user to reconnect Google Calendar.");
        }
        long expiresAtEpoch = Instant.now().getEpochSecond() + Math.max(60, token.expiresInSeconds());
        connectionService.saveGoogleCalendar(userId, token.accessToken(), account.refreshToken(),
                expiresAtEpoch, account.displayName());
        account = new ConnectionService.GoogleCalendarAccount(token.accessToken(), account.refreshToken(),
                expiresAtEpoch, account.displayName());
        return token.accessToken();
    }

    private static String refused(RestClientResponseException e) {
        if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            return "ERROR: Google Calendar rejected the request (" + e.getStatusCode()
                    + "). The connection may be revoked — ask the user to reconnect Google Calendar "
                    + "on the Integrations page.";
        }
        String body = e.getResponseBodyAsString();
        return "ERROR: Google Calendar refused (" + e.getStatusCode() + "): "
                + (body.length() <= 300 ? body : body.substring(0, 300) + "…");
    }
}
