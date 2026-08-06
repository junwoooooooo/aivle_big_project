package com.aivle.backend.document.dto.request;

import jakarta.validation.constraints.NotNull;

public record ConfirmStructuredPlanRequest(@NotNull Long version) {
}
