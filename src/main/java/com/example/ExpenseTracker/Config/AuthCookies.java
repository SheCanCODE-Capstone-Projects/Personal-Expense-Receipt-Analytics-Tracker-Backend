package com.example.ExpenseTracker.Config;

import com.example.ExpenseTracker.DTO.AuthenticationDTO.Tokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Carries the JWT pair in HttpOnly cookies so browser JavaScript never sees or stores a token.
 * The access cookie is sent to every /api call; the refresh cookie only to /api/auth (refresh and logout).
 */
@Component
public class AuthCookies {
    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;
    private final String domain;

    public AuthCookies(@Value("${app.auth.cookie.secure:true}") boolean secure,
                       @Value("${app.auth.cookie.same-site:Strict}") String sameSite,
                       @Value("${app.auth.cookie.domain:}") String domain) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain == null || domain.isBlank() ? null : domain.trim();
    }

    public void write(HttpServletResponse response, Tokens tokens) {
        add(response, cookie(ACCESS, tokens.accessToken(), ACCESS_PATH, Duration.ofMinutes(15)));
        add(response, cookie(REFRESH, tokens.refreshToken(), REFRESH_PATH, Duration.ofDays(30)));
    }

    public void clear(HttpServletResponse response) {
        add(response, cookie(ACCESS, "", ACCESS_PATH, Duration.ZERO));
        add(response, cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO));
    }

    public Optional<String> accessToken(HttpServletRequest request) {
        return read(request, ACCESS);
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return read(request, REFRESH);
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        var builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge);
        if (domain != null) builder.domain(domain);
        return builder.build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }
}
