package com.sahayak.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlGuardTest {

    @Test
    void allowsNormalPublicUrls() {
        // literal public IP: no DNS lookup needed, deterministic in tests
        assertNull(UrlGuard.check("https://93.184.216.34/article"));
        assertNull(UrlGuard.check("http://93.184.216.34:80/x?y=1"));
    }

    @Test
    void blocksLocalhostAndLoopback() {
        assertNotNull(UrlGuard.check("http://localhost/admin"));
        assertNotNull(UrlGuard.check("http://127.0.0.1/"));
        assertNotNull(UrlGuard.check("https://[::1]/"));
        assertNotNull(UrlGuard.check("http://sub.localhost/"));
    }

    @Test
    void blocksPrivateNetworkRanges() {
        assertNotNull(UrlGuard.check("http://10.0.0.5/"));
        assertNotNull(UrlGuard.check("http://192.168.1.1/router"));
        assertNotNull(UrlGuard.check("http://172.16.0.1/"));
        assertNotNull(UrlGuard.check("http://169.254.169.254/latest/meta-data")); // cloud metadata
    }

    @Test
    void blocksWrongSchemesAndPorts() {
        assertNotNull(UrlGuard.check("ftp://example.com/file"));
        assertNotNull(UrlGuard.check("file:///etc/passwd"));
        assertNotNull(UrlGuard.check("http://93.184.216.34:8080/"));
        assertNotNull(UrlGuard.check("not a url at all"));
        assertNotNull(UrlGuard.check("http:///nohost"));
    }

    @Test
    void blocksInternalLookingHostNames() {
        assertNotNull(UrlGuard.check("http://printer.local/"));
        assertNotNull(UrlGuard.check("http://db.internal/"));
    }
}
