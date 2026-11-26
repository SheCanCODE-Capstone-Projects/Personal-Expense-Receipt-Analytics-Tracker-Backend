package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.Model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Signs and verifies the HS256 JWTs handed to clients. Access tokens carry the user's identity as claims
 * for the frontend to decode; refresh tokens carry only what is needed to find the session.
 */
@Component
public class JwtService {

    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    private static final String TYPE_CLAIM = "token_type";

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;

    public JwtService(@Value("${app.auth.jwt-secret}") String secret,
                      @Value("${app.auth.jwt-issuer:expense-tracker}") String issuer) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret (JWT_SECRET) must be at least 32 bytes");
        }
        SecretKey secretKey = new SecretKeySpec(key, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.issuer = issuer;
    }

    public String accessToken(User user, UUID sessionId, UUID jti, Instant issuedAt, Instant expiresAt) {
        return encode(JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(jti.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TYPE_CLAIM, ACCESS)
                .claim("sid", sessionId.toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .build());
    }

    public String refreshToken(User user, UUID sessionId, UUID jti, Instant issuedAt, Instant expiresAt) {
        return encode(JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(jti.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TYPE_CLAIM, REFRESH)
                .claim("sid", sessionId.toString())
                .build());
    }

    /** Verifies signature, issuer and expiry, and that the token is of the expected kind. */
    public Jwt decode(String token, String expectedType) {
        Jwt jwt = decoder.decode(token);
        if (!expectedType.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
            throw new BadJwtException("Expected a " + expectedType + " token");
        }
        return jwt;
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
