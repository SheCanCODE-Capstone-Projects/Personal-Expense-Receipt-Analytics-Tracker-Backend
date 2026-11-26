package com.example.ExpenseTracker.controller;

import com.example.ExpenseTracker.Config.AuthCookies;
import com.example.ExpenseTracker.DTO.AuthenticationDTO.*;
import com.example.ExpenseTracker.service.AuthenticationService;
import com.example.ExpenseTracker.service.JwtService;
import com.example.ExpenseTracker.Exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Tag(name = "Authentication", description = "Request an email code, then verify it (or log in with a password). "
        + "Tokens are returned only as HttpOnly cookies (access_token for /api, refresh_token for /api/auth), "
        + "which this browser then sends automatically, including from Swagger UI.")
@ApiResponse(responseCode = "400", description = "Invalid request body",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService auth;
    private final AuthCookies cookies;
    private final JwtService jwt;

    @Operation(summary = "Request an email sign-in code",
            description = "Sends a six-digit code valid for 10 minutes. Repeat requests within 60 seconds receive the same status without sending another email.")
    @SecurityRequirements
    @ApiResponse(responseCode = "202", description = "Code request accepted", content = @Content)
    @ApiResponse(responseCode = "503", description = "Email delivery unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@Valid @RequestBody EmailRequest request) {
        auth.requestCode(request.email());
    }

    @Operation(summary = "Verify the code and sign in (or register)",
            description = "Each code can be used once and allows five guesses. The first time an email verifies, fullName and password "
                    + "are required and an account is created with the chosen role (any role except ADMIN; default MEMBER). "
                    + "Sets the session cookies and returns the signed-in user's profile.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Signed in; session cookies set", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", description = "Invalid, expired, exhausted, or previously used code",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/otp/verify")
    public Profile verify(@Valid @RequestBody VerifyRequest request, HttpServletResponse response) {
        return signIn(auth.verify(request), response);
    }

    @Operation(summary = "Sign in with email and password",
            description = "For accounts that set a password at registration. Sets the session cookies and returns the profile.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Signed in; session cookies set", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public Profile login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return signIn(auth.login(request.email(), request.password()), response);
    }

    @Operation(summary = "Rotate access and refresh tokens",
            description = "Uses the refresh_token cookie (or, for non-browser clients, a refreshToken in the body). "
                    + "Rotation invalidates the previous pair. The session expires 30 days after sign-in.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "New session cookies set", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", description = "Missing, invalid, expired, or previously used refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/refresh")
    public Profile refresh(@RequestBody(required = false) RefreshRequest body,
                           HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookies.refreshToken(request)
                .orElse(body != null && body.refreshToken() != null && !body.refreshToken().isBlank() ? body.refreshToken() : null);
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        try {
            return signIn(auth.refresh(refreshToken), response);
        } catch (ResponseStatusException ex) {
            cookies.clear(response);
            throw ex;
        }
    }

    @Operation(summary = "Sign out this session",
            description = "Invalidates the session of the access token in the cookie or Authorization header, and clears the cookies. "
                    + "Always succeeds, so a browser can sign out even when its session already expired.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Signed out", content = @Content)
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                       HttpServletRequest request, HttpServletResponse response) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim()
                : cookies.accessToken(request).orElse(null);
        if (token != null) {
            try {
                auth.logout(token);
            } catch (ResponseStatusException ignored) {
                // Already signed out or expired: clearing the cookies is all that is left to do.
            }
        }
        cookies.clear(response);
    }

    @Operation(summary = "Sign out everywhere", description = "Invalidates every session for the signed-in user.")
    @ApiResponse(responseCode = "204", description = "All sessions ended", content = @Content)
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal UUID userId, HttpServletResponse response) {
        auth.logoutEverywhere(userId);
        cookies.clear(response);
    }

    @Operation(summary = "Get the signed-in user's profile")
    @GetMapping("/me")
    public Profile me(@AuthenticationPrincipal UUID userId) {
        return auth.profile(userId);
    }

    @Operation(summary = "Update the signed-in user's display name")
    @PutMapping("/me")
    public Profile updateMe(@AuthenticationPrincipal UUID userId, @Valid @RequestBody ProfileUpdate request) {
        return auth.updateProfile(userId, request.fullName());
    }

    private Profile signIn(Tokens tokens, HttpServletResponse response) {
        cookies.write(response, tokens);
        return auth.profile(UUID.fromString(jwt.decode(tokens.accessToken(), JwtService.ACCESS).getSubject()));
    }
}
