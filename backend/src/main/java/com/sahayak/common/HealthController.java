package com.sahayak.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public "is the server up" check. Reports real facts, not decorative ones:
 * the database is actually pinged, and uptime is measured — the dashboard's
 * status cards are built from this.
 */
@RestController
public class HealthController {

    private static final long STARTED_AT = System.currentTimeMillis();

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        String database;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            database = "connected";
        } catch (Exception e) {
            database = "error";
        }
        return Map.of(
                "status", "ok",
                "database", database,
                "uptimeSeconds", (System.currentTimeMillis() - STARTED_AT) / 1000);
    }
}
