package com.sahayak.web;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Safety check for AI/user-supplied URLs before the server fetches them.
 * Blocks anything that could reach the server's own machine or the private
 * network (SSRF protection), and anything that is not plain http(s) on the
 * standard web ports.
 */
public final class UrlGuard {

    private UrlGuard() {
    }

    /** Returns null when the URL is safe to fetch, otherwise a human-readable reason. */
    public static String check(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return "that is not a valid URL";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return "only http and https URLs can be fetched";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "the URL has no host name";
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return "only the standard web ports (80/443) are allowed";
        }
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost")
                || lower.endsWith(".local") || lower.endsWith(".internal")) {
            return "local and internal addresses are blocked";
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return "the host name could not be resolved";
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                return "addresses inside the local or private network are blocked";
            }
        }
        return null;
    }

    /** fc00::/7 — the IPv6 equivalent of private 10.x/192.168.x ranges. */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
