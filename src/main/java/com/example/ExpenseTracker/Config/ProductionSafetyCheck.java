package com.example.ExpenseTracker.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/** Refuses to start production with settings that are only safe for local demos. */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    public ProductionSafetyCheck(
            @Value("${app.auth.log-codes:false}") boolean logCodes,
            @Value("${app.demo.seed:false}") boolean demoSeed,
            @Value("${app.cors.allowed-origins}") List<String> origins,
            @Value("${app.auth.jwt-secret}") String jwtSecret) {
        if (jwtSecret.startsWith("dev-only")) {
            throw new IllegalStateException("JWT_SECRET must be set to a private random value in production");
        }
        if (logCodes) {
            throw new IllegalStateException("app.auth.log-codes must be false in production: it writes sign-in codes to the log");
        }
        if (demoSeed) {
            throw new IllegalStateException("app.demo.seed must be false in production");
        }
        if (origins.stream().anyMatch(o -> o.contains("localhost") || o.contains("127.0.0.1") || o.equals("*"))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list the production frontend origin(s) only");
        }
    }
}
