package com.sahayak.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Reads the "Authorization: Bearer ..." header and logs the request in if the token is valid. */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    /** Request attribute holding the raw token, so /api/auth/logout can revoke it. */
    public static final String RAW_TOKEN_ATTRIBUTE = "sahayak.rawToken";

    private final AuthService authService;

    public TokenAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String rawToken = header.substring(7).trim();
            authService.authenticate(rawToken).ifPresent(user -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        AuthenticatedUser.from(user), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(RAW_TOKEN_ATTRIBUTE, rawToken);
            });
        }
        chain.doFilter(request, response);
    }
}
