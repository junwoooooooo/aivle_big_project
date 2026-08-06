package com.aivle.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 50) String displayName,
    @Email @Size(max = 254) String email,
    @Size(max = 120) String organizationName,
    @Size(max = 120) String departmentName,
    @Size(max = 120) String jobTitle
) {}
