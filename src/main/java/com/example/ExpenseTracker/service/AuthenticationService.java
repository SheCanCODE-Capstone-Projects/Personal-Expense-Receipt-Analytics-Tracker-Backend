package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.DTO.AuthenticationDTO.Profile;
import com.example.ExpenseTracker.DTO.AuthenticationDTO.Tokens;
import com.example.ExpenseTracker.DTO.AuthenticationDTO.VerifyRequest;
import com.example.ExpenseTracker.Model.*;
import com.example.ExpenseTracker.Repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    private final EmailChallengeRepository challenges;
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final SignInMailer mail;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Marks accounts created before passwords existed; it never matches a BCrypt comparison.
    private static final String NO_PASSWORD = "!";
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("dummy-password-for-timing");

    // Local demos only: write the code to the server log instead of emailing it.
    @Value("${app.auth.log-codes:false}")
    private boolean logCodes;

    @Transactional
    public void requestCode(String address) {
        String email = address.toLowerCase(Locale.ROOT);
        challenges.ensureRow(email);
        EmailChallenge challenge = challenges.findByEmail(email).orElseThrow();
        Instant now = Instant.now();
        if (challenge.getIssuedAt() != null && challenge.getIssuedAt().plusSeconds(60).isAfter(now)) {
            return;
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        challenge.setCodeHash(encoder.encode(code));
        challenge.setIssuedAt(now);
        challenge.setAttempts(0);

        if (logCodes) {
            log.warn("Sign-in code for {}: {} (app.auth.log-codes is on; never enable this in production)", email, code);
            return;
        }

        String recipient = users.findByEmailIgnoreCase(email).map(User::getEmail).orElse(address);
        try {
            mail.send(recipient, "Your Expense Tracker sign-in code",
                    "Your code is " + code + ". It expires in 10 minutes.");
        } catch (MailException ex) {
            // The client only sees a generic 503; the log records why the SMTP server refused.
            log.error("Sign-in email to {} failed: {}", email, ex.getMostSpecificCause().getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery unavailable", ex);
        }
    }

    // An incorrect guess must consume an attempt even when verification returns 401.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens verify(VerifyRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        EmailChallenge challenge = challenges.findByEmail(email).orElseThrow(AuthenticationService::invalidCode);
        Instant now = Instant.now();
        if (challenge.getCodeHash() == null || !challenge.getIssuedAt().plusSeconds(600).isAfter(now)
                || challenge.getAttempts() >= 5) {
            throw invalidCode();
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (!encoder.matches(request.code(), challenge.getCodeHash())) {
            throw invalidCode();
        }
        // Registration details are checked only after a correct code, so this can't reveal which emails exist.
        // The code is not consumed when they are missing, so the client can resubmit with them.
        User user = users.findByEmailIgnoreCase(email).orElseGet(() -> register(email, request));
        if (NO_PASSWORD.equals(user.getHashPassword()) && request.password() != null) {
            // Accounts created before passwords existed can set one the next time they verify a code.
            user.setHashPassword(encoder.encode(request.password()));
            if (request.fullName() != null && !request.fullName().isBlank()) {
                user.setFullName(request.fullName().trim());
            }
        }
        challenge.setCodeHash(null);
        return startSession(user, now);
    }

    @Transactional
    public Tokens login(String address, String password) {
        Optional<User> user = users.findByEmailIgnoreCase(address.trim());
        // Compare against a dummy hash when the account is missing so response time doesn't reveal it.
        String hash = user.map(User::getHashPassword).filter(h -> !NO_PASSWORD.equals(h)).orElse(DUMMY_HASH);
        boolean matches = encoder.matches(password, hash);
        if (!matches || DUMMY_HASH.equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return startSession(user.get(), Instant.now());
    }

    @Transactional
    public Tokens refresh(String refreshToken) {
        Jwt jwt = decodeOr401(refreshToken, JwtService.REFRESH, AuthenticationService::invalidRefresh);
        AuthSession session = sessions.findByRefreshHash(tokenHash(jwt.getId()))
                .orElseThrow(AuthenticationService::invalidRefresh);
        Instant now = Instant.now();
        if (!session.getRefreshExpiresAt().isAfter(now) || !session.getId().toString().equals(jwt.getClaimAsString("sid"))) {
            throw invalidRefresh();
        }
        return issueTokens(session, now);
    }

    private User register(String email, VerifyRequest request) {
        if (request.fullName() == null || request.fullName().isBlank() || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New account: fullName and password are required to finish registration");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setRole(selectableRole(request.role()));
        user.setHashPassword(encoder.encode(request.password()));
        return users.save(user);
    }

    /** Users choose their own role at sign-up, except ADMIN, which would let anyone grant themselves full access. */
    static ERole selectableRole(String requested) {
        if (requested == null || requested.isBlank()) {
            return ERole.MEMBER;
        }
        for (ERole role : ERole.values()) {
            if (role.name().equalsIgnoreCase(requested.trim())) {
                if (role == ERole.ADMIN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The ADMIN role cannot be chosen at sign-up");
                }
                return role;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + requested);
    }

    private Tokens startSession(User user, Instant now) {
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setRefreshExpiresAt(now.plus(Duration.ofDays(30)));
        return issueTokens(session, now);
    }

    private Tokens issueTokens(AuthSession session, Instant now) {
        // The database keeps only hashes of each token's jti, so a leaked table can't be replayed,
        // and rotating them here invalidates the previous pair.
        UUID accessJti = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        session.setAccessHash(tokenHash(accessJti.toString()));
        session.setRefreshHash(tokenHash(refreshJti.toString()));
        Instant expiresAt = now.plus(Duration.ofMinutes(15));
        session.setAccessExpiresAt(expiresAt.isBefore(session.getRefreshExpiresAt()) ? expiresAt : session.getRefreshExpiresAt());
        AuthSession saved = sessions.save(session);
        User user = saved.getUser();
        return new Tokens(
                jwt.accessToken(user, saved.getId(), accessJti, now, saved.getAccessExpiresAt()),
                jwt.refreshToken(user, saved.getId(), refreshJti, now, saved.getRefreshExpiresAt()));
    }

    /** Ends the session that owns this access token. */
    @Transactional
    public void logout(String accessToken) {
        Jwt token = decodeOr401(accessToken, JwtService.ACCESS, AuthenticationService::invalidSession);
        sessions.findByAccessHash(tokenHash(token.getId())).ifPresent(sessions::delete);
    }

    private Jwt decodeOr401(String token, String type, java.util.function.Supplier<ResponseStatusException> error) {
        try {
            return jwt.decode(token, type);
        } catch (JwtException ex) {
            throw error.get();
        }
    }

    /** Ends every session for the user, signing them out on all devices. */
    @Transactional
    public int logoutEverywhere(UUID userId) {
        return sessions.deleteAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID userId) {
        return toProfile(users.findById(userId).orElseThrow(AuthenticationService::invalidSession));
    }

    @Transactional
    public Profile updateProfile(UUID userId, String fullName) {
        User user = users.findById(userId).orElseThrow(AuthenticationService::invalidSession);
        user.setFullName(fullName.trim());
        return toProfile(user);
    }

    @Scheduled(cron = "${app.auth.session-cleanup-cron:0 17 * * * *}")
    @Transactional
    public void deleteExpiredSessions() {
        int removed = sessions.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Removed {} expired sign-in session(s)", removed);
        }
    }

    private static Profile toProfile(User user) {
        return new Profile(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), user.getCreatedAt());
    }

    public static String tokenHash(String jti) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(jti.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ResponseStatusException invalidCode() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
    }

    private static ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is no longer valid");
    }

    private static ResponseStatusException invalidRefresh() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
