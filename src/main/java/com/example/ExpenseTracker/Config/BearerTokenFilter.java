package com.example.ExpenseTracker.Config;

import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.AuthSessionRepository;
import com.example.ExpenseTracker.service.AuthenticationService;
import com.example.ExpenseTracker.service.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Authenticates a request from either an "Authorization: Bearer" header (API clients, Swagger, tests)
 * or the HttpOnly access-token cookie (the browser app).
 */
@RequiredArgsConstructor
class BearerTokenFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AuthSessionRepository sessions;
    private final JwtService jwtService;
    private final AuthCookies cookies;
    private final List<String> allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            // An explicit bearer token that fails is always an error.
            if (!authenticate(header.substring(7).trim())) {
                response.setStatus(401);
                return;
            }
        } else if (cookies.accessToken(request).isPresent() || cookies.refreshToken(request).isPresent()) {
            // Browsers attach cookies to cross-site requests too, so state-changing calls that carry
            // auth cookies must come from an allowed origin (or this server). This is the CSRF defence.
            if (!SAFE_METHODS.contains(request.getMethod()) && !trustedOrigin(request)) {
                response.setStatus(403);
                return;
            }
            // A stale cookie is ignored rather than rejected, so public endpoints such as
            // /api/auth/refresh and /api/auth/login keep working; protected ones still answer 401.
            cookies.accessToken(request).ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private boolean authenticate(String token) {
        Jwt jwt;
        try {
            jwt = jwtService.decode(token, JwtService.ACCESS);
        } catch (JwtException ex) {
            return false;
        }
        // A valid signature isn't enough: the session must still exist and this must be its current
        // access token, so logout and token rotation take effect immediately.
        var session = sessions.findByAccessHashAndAccessExpiresAtAfter(AuthenticationService.tokenHash(jwt.getId()), Instant.now());
        if (session.isEmpty() || !session.get().getId().toString().equals(jwt.getClaimAsString("sid"))) {
            return false;
        }
        User user = session.get().getUser();
        var auth = UsernamePasswordAuthenticationToken.authenticated(user.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return true;
    }

    private boolean trustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            String referer = request.getHeader("Referer");
            if (referer == null || referer.isBlank()) return false;
            try {
                URI uri = URI.create(referer);
                origin = uri.getScheme() + "://" + uri.getRawAuthority();
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return allowedOrigins.contains(origin) || origin.equalsIgnoreCase(selfOrigin(request));
    }

    private static String selfOrigin(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
