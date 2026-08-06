package com.aivle.backend.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServerHealthResponse(
    String status,
    String service,
    @JsonProperty("request_id")
    String requestId
) {
}
