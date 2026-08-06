package com.aivle.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

public record LoginRequest(
    @NotBlank
    @Pattern(regexp = UsernamePolicy.FORMAT)
    String username,
    @NotBlank String password
) {
    public LoginRequest { username = username == null ? null : username.trim().toLowerCase(Locale.ROOT); }
}
