package com.example.ExpenseTracker;

import com.example.ExpenseTracker.DTO.AuthenticationDTO.Tokens;
import com.example.ExpenseTracker.Repository.EmailChallengeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mail;
    @Autowired EmailChallengeRepository challenges;
    @Autowired com.example.ExpenseTracker.Repository.UserRepository users;

    private ResultActions submit(String path, Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/auth/" + path).contentType("application/json").content(json.writeValueAsBytes(body)));
    }

    private String requestCode(String email) throws Exception {
        submit("otp/request", Map.of("email", email)).andExpect(status().isAccepted());
        submit("otp/request", Map.of("email", email)).andExpect(status().isAccepted());
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertArrayEquals(new String[]{email}, sent.getValue().getTo());
        var matcher = Pattern.compile("[0-9]{6}").matcher(sent.getValue().getText());
        assertTrue(matcher.find());
        return matcher.group();
    }

    private Tokens tokens(ResultActions response) throws Exception {
        var result = response.andExpect(status().isOk()).andReturn().getResponse();
        // Tokens travel only in HttpOnly cookies; the body is the profile and never contains a token.
        Map<?, ?> body = json.readValue(result.getContentAsByteArray(), Map.class);
        assertFalse(body.containsKey("accessToken") || body.containsKey("refreshToken"), "tokens must not be in the body");
        assertNotNull(body.get("userId"));
        var access = result.getCookie("access_token");
        var refresh = result.getCookie("refresh_token");
        assertNotNull(access, "access_token cookie");
        assertNotNull(refresh, "refresh_token cookie");
        for (var cookie : new jakarta.servlet.http.Cookie[]{access, refresh}) {
            assertTrue(cookie.isHttpOnly(), cookie.getName() + " must be HttpOnly");
            assertTrue(cookie.getSecure(), cookie.getName() + " must be Secure");
        }
        assertEquals("/api", access.getPath());
        assertEquals("/api/auth", refresh.getPath());
        String setCookie = String.join(";", result.getHeaders("Set-Cookie"));
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        return new Tokens(access.getValue(), refresh.getValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> claims(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        return json.readValue(java.util.Base64.getUrlDecoder().decode(parts[1]), Map.class);
    }

    private static Map<String, String> registration(String email, String code) {
        return Map.of("email", email, "code", code, "fullName", "Amina Uwase", "password", "Str0ngPass", "role", "TREASURER");
    }

    private ResultActions budgets(String bearer) throws Exception {
        return mvc.perform(get("/api/budgets").param("month", "2026-11")
                .header("Authorization", "Bearer " + bearer).header("X-User-Id", "-1"));
    }

    @Test
    void swaggerDocumentsPublicSignInAndProtectedRoutes() throws Exception {
        var docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String path : new String[]{"otp/request", "otp/verify", "refresh"}) {
            String operation = "$.paths['/api/auth/" + path + "'].post";
            docs.andExpect(jsonPath(operation + ".tags[0]").value("Authentication"))
                    .andExpect(jsonPath(operation + ".security").isArray())
                    .andExpect(jsonPath(operation + ".security").isEmpty());
        }
        docs.andExpect(jsonPath("$.security[0].BearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/otp/request'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/otp/verify'].post.responses['200'].content.*.schema['$ref']").value(org.hamcrest.Matchers.hasItem("#/components/schemas/Profile")));
    }

    @Test
    void verifyOnceAndRotateBothTokens() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var proof = registration(email, requestCode(email));
        Tokens first = tokens(submit("otp/verify", proof));
        submit("otp/verify", proof).andExpect(status().isUnauthorized());
        budgets(first.accessToken()).andExpect(status().isOk());
        budgets(first.refreshToken()).andExpect(status().isUnauthorized());

        var refresh = Map.of("refreshToken", first.refreshToken());
        Tokens second = tokens(submit("refresh", refresh));
        assertEquals(claims(first.accessToken()).get("sub"), claims(second.accessToken()).get("sub"));
        assertNotEquals(first.refreshToken(), second.refreshToken());
        submit("refresh", refresh).andExpect(status().isUnauthorized());
        budgets(first.accessToken()).andExpect(status().isUnauthorized());
        budgets(second.accessToken()).andExpect(status().isOk());
    }

    @Test
    void profileLogoutAndLogoutEverywhere() throws Exception {
        String email = UUID.randomUUID() + "@Example.test";
        Tokens session = tokens(submit("otp/verify", registration(email, requestCode(email))));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(claims(session.accessToken()).get("sub")))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("TREASURER"));
        mvc.perform(put("/api/auth/me").header("Authorization", "Bearer " + session.accessToken())
                        .contentType("application/json").content("{\"fullName\":\"  Amina Uwase \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Amina Uwase"));
        mvc.perform(put("/api/auth/me").header("Authorization", "Bearer " + session.accessToken())
                .contentType("application/json").content("{\"fullName\":\"\"}")).andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());
        budgets(session.accessToken()).andExpect(status().isUnauthorized());
        submit("refresh", Map.of("refreshToken", session.refreshToken())).andExpect(status().isUnauthorized());
        // Logout is idempotent so a browser can always clear its cookies, even without a session.
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void logoutEverywhereEndsEverySession() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        Tokens phone = tokens(submit("otp/verify", registration(email, requestCode(email))));

        // Skip the 60-second resend throttle so the same user can sign in on a second device.
        var challenge = challenges.findByEmail(email).orElseThrow();
        challenge.setIssuedAt(challenge.getIssuedAt().minusSeconds(120));
        submit("otp/request", Map.of("email", email)).andExpect(status().isAccepted());
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(sent.capture());
        var matcher = Pattern.compile("[0-9]{6}").matcher(sent.getAllValues().get(1).getText());
        assertTrue(matcher.find());
        Tokens laptop = tokens(submit("otp/verify", Map.of("email", email, "code", matcher.group())));
        assertNotEquals(phone.accessToken(), laptop.accessToken());
        budgets(phone.accessToken()).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + laptop.accessToken()))
                .andExpect(status().isNoContent());
        budgets(phone.accessToken()).andExpect(status().isUnauthorized());
        budgets(laptop.accessToken()).andExpect(status().isUnauthorized());
        submit("refresh", Map.of("refreshToken", phone.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void firstVerificationRegistersNamePasswordAndChosenRole() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String code = requestCode(email);

        // A correct code without registration details is rejected but not used up.
        submit("otp/verify", Map.of("email", email, "code", code)).andExpect(status().isBadRequest());
        var admin = new java.util.HashMap<>(registration(email, code));
        admin.put("role", "admin");
        submit("otp/verify", admin).andExpect(status().isBadRequest());
        var weak = new java.util.HashMap<>(registration(email, code));
        weak.put("password", "short");
        submit("otp/verify", weak).andExpect(status().isBadRequest());

        Tokens tokens = tokens(submit("otp/verify", registration(email, code)));
        Map<String, Object> access = claims(tokens.accessToken());
        assertEquals(email, access.get("email"));
        assertEquals("Amina Uwase", access.get("name"));
        assertEquals("TREASURER", access.get("role"));
        assertEquals("access", access.get("token_type"));
        assertNotNull(access.get("exp"));
        Map<String, Object> refresh = claims(tokens.refreshToken());
        assertEquals("refresh", refresh.get("token_type"));
        assertNull(refresh.get("email"));

        String hash = users.findByEmailIgnoreCase(email).orElseThrow().getHashPassword();
        assertTrue(hash.startsWith("$2"), "password must be stored as a BCrypt hash");
        assertFalse(hash.contains("Str0ngPass"));
    }

    @Test
    void passwordLoginAndTamperedTokens() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        tokens(submit("otp/verify", registration(email, requestCode(email))));

        Tokens login = tokens(submit("login", Map.of("email", email.toUpperCase(), "password", "Str0ngPass")));
        budgets(login.accessToken()).andExpect(status().isOk());
        submit("login", Map.of("email", email, "password", "WrongPass1")).andExpect(status().isUnauthorized());
        submit("login", Map.of("email", "nobody-" + email, "password", "Str0ngPass")).andExpect(status().isUnauthorized());

        // Changing a claim (e.g. promoting yourself to ADMIN) breaks the signature.
        String[] parts = login.accessToken().split("\\.");
        String forged = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("TREASURER", "ADMIN").getBytes()) + "." + parts[2];
        budgets(forged).andExpect(status().isUnauthorized());
        budgets("not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void fiveIncorrectCodesExhaustTheChallenge() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String code = requestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int guess = 0; guess < 5; guess++) {
            submit("otp/verify", Map.of("email", email, "code", wrong)).andExpect(status().isUnauthorized());
        }
        submit("otp/verify", Map.of("email", email, "code", code)).andExpect(status().isUnauthorized());
    }

    @Test
    void browserCookieSessionRefreshAndLogout() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        Tokens session = tokens(submit("otp/verify", registration(email, requestCode(email))));
        var accessCookie = new jakarta.servlet.http.Cookie("access_token", session.accessToken());
        var refreshCookie = new jakarta.servlet.http.Cookie("refresh_token", session.refreshToken());
        String app = "http://localhost:5173";

        // The access cookie alone authenticates reads.
        mvc.perform(get("/api/auth/me").cookie(accessCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        // Cookie-authenticated writes must come from an allowed origin (CSRF defence).
        var update = Map.of("fullName", "Amina U.");
        mvc.perform(put("/api/auth/me").cookie(accessCookie).contentType("application/json")
                .content(json.writeValueAsBytes(update))).andExpect(status().isForbidden());
        mvc.perform(put("/api/auth/me").cookie(accessCookie).header("Origin", "https://evil.example")
                .contentType("application/json").content(json.writeValueAsBytes(update))).andExpect(status().isForbidden());
        mvc.perform(put("/api/auth/me").cookie(accessCookie).header("Origin", app)
                .contentType("application/json").content(json.writeValueAsBytes(update))).andExpect(status().isOk());

        // Refresh works from the cookie even with a stale access cookie alongside it, and rotates both.
        var stale = new jakarta.servlet.http.Cookie("access_token", "not-a-jwt");
        Tokens rotated = tokens(mvc.perform(post("/api/auth/refresh").cookie(stale, refreshCookie).header("Origin", app)));
        assertNotEquals(session.refreshToken(), rotated.refreshToken());
        mvc.perform(get("/api/auth/me").cookie(accessCookie)).andExpect(status().isUnauthorized());

        // Logout ends the session and expires both cookies.
        var loggedOut = mvc.perform(post("/api/auth/logout").header("Origin", app)
                        .cookie(new jakarta.servlet.http.Cookie("access_token", rotated.accessToken())))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertEquals(0, loggedOut.getCookie("access_token").getMaxAge());
        assertEquals(0, loggedOut.getCookie("refresh_token").getMaxAge());
        mvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("access_token", rotated.accessToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Origin", app)
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", rotated.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutAnyTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }
}
