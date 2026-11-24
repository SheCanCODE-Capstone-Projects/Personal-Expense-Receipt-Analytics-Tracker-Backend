package com.example.ExpenseTracker.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthenticationDTO {
    private AuthenticationDTO() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserResponse(Long id, String fullName, String email, String defaultCurrency) {}

    public record AuthResponse(String token, UserResponse user) {}
}