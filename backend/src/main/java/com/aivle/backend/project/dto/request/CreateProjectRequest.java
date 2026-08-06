package com.aivle.backend.project.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CreateProjectRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 10000) String description,
        @Size(max = 100) String industryCategory) {}
