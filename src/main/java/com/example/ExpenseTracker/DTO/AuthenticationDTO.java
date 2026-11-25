package com.example.ExpenseTracker.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AuthenticationDTO {
    private AuthenticationDTO() {}

    public record EmailRequest(@Schema(example = "member@example.com") @NotBlank @Email @Size(max = 254) String email) {}

    @Schema(description = "fullName, password and role are required the first time an email signs in (registration) and ignored afterwards.")
    public record VerifyRequest(@Schema(example = "member@example.com") @NotBlank @Email @Size(max = 254) String email,
                                @Schema(example = "012345", description = "Six digits from the email, including any leading zeros")
                                @NotNull @Pattern(regexp = "[0-9]{6}") String code,
                                @Schema(example = "Amina Uwase") @Size(max = 100) String fullName,
                                @Schema(example = "Str0ngPassw0rd", description = "8 to 72 characters with at least one letter and one digit")
                                @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain a letter and a digit") String password,
                                @Schema(example = "MEMBER", description = "Any role except ADMIN; defaults to MEMBER") String role) {}

    public record LoginRequest(@Schema(example = "member@example.com") @NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @Schema(description = "Both values are signed JWTs. Decode the access token on the client to read sub (user id), email, name, role and exp.")
    public record Tokens(@Schema(description = "Bearer credential, valid for 15 minutes") String accessToken,
                         @Schema(description = "Single-use credential for /api/auth/refresh, valid for up to 30 days") String refreshToken) {}

    public record Profile(UUID userId, String email, String fullName, String role, LocalDateTime createdAt) {}
    public record ProfileUpdate(@Schema(example = "Amina Uwase") @NotBlank @Size(max = 100) String fullName) {}
}
