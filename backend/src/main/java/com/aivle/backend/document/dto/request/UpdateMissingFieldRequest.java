package com.aivle.backend.document.dto.request;

import com.aivle.backend.common.entity.MissingFieldStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMissingFieldRequest(
    @NotNull MissingFieldStatus status,
    @Size(max = 4000) String value,
    @Size(max = 500) String reason,
    @NotNull Long version
) {
}
