package com.aivle.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequest(
    @NotBlank String password,
    @NotBlank String confirmation,
    @Size(max = 500) String reason
) { }
