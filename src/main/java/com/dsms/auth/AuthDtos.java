package com.dsms.auth;

import com.dsms.common.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @NotNull UserRole role,
            String timezone
    ) {}

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @Size(min = 10, max = 100) String newPassword) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @Size(min = 10, max = 100) String newPassword) {}

    public record CsrfResponse(String token, String headerName) {}

    public record CurrentUserResponse(
            String id,
            String email,
            String displayName,
            String role,
            String status,
            String timezone,
            boolean mustChangePassword
    ) {}
}
