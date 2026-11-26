package com.example.ExpenseTracker.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/** Opening the bare API URL in a browser lands on Swagger UI when it is enabled. */
@Hidden
@RestController
public class RootController {
    private final boolean swaggerEnabled;

    public RootController(@Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerEnabled) {
        this.swaggerEnabled = swaggerEnabled;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        if (swaggerEnabled) {
            // Relative redirect, so it keeps the https scheme used by the proxy.
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("swagger-ui/index.html")).build();
        }
        return ResponseEntity.ok(Map.of("service", "Personal Expense Tracker API", "health", "/actuator/health"));
    }
}
