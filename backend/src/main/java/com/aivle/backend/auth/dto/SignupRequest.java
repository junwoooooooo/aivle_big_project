package com.aivle.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record SignupRequest(
    @NotBlank @Pattern(regexp = UsernamePolicy.FORMAT) String username,
    @NotBlank String password,
    @NotBlank @Size(max = 50) String displayName,
    @Email @Size(max = 254) String email,
    @Size(max = 120) String organizationName,
    @Size(max = 120) String departmentName,
    @Size(max = 120) String jobTitle
) {
    public SignupRequest {
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        email = optionalEmail(email);
        organizationName = optionalText(organizationName); departmentName = optionalText(departmentName); jobTitle = optionalText(jobTitle);
    }
    private static String optionalEmail(String value) { String normalized = optionalText(value); return normalized == null ? null : normalized.toLowerCase(Locale.ROOT); }
    private static String optionalText(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
