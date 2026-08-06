package com.aivle.backend.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServerErrorResponse(
    @JsonProperty("request_id")
    String requestId,
    ErrorDetail error
) {
    public record ErrorDetail(
        String code,
        String message,
        boolean retryable
    ) {
    }
}
