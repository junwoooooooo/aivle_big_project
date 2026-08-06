package com.aivle.backend.integration.ai.task.dto;

import com.aivle.backend.integration.ai.dto.AiServerErrorResponse;
import com.aivle.backend.integration.ai.task.AiTaskType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record AiTaskResponse(
    @JsonProperty("request_id")
    String requestId,
    @JsonProperty("task_id")
    String taskId,
    @JsonProperty("task_type")
    AiTaskType taskType,
    String status,
    @JsonProperty("schema_version")
    String schemaVersion,
    JsonNode result,
    List<String> warnings,
    Execution execution,
    AiServerErrorResponse.ErrorDetail error,
    List<ArtifactMetadata> artifacts
) {
    public AiTaskResponse(
        String requestId,
        String taskId,
        AiTaskType taskType,
        String status,
        String schemaVersion,
        JsonNode result,
        List<String> warnings,
        Execution execution,
        AiServerErrorResponse.ErrorDetail error
    ) {
        this(
            requestId,
            taskId,
            taskType,
            status,
            schemaVersion,
            result,
            warnings,
            execution,
            error,
            List.of()
        );
    }

    public record Execution(
        String handler,
        @JsonProperty("handler_version")
        String handlerVersion
    ) {
    }

    public record ArtifactMetadata(
        String role,
        @JsonProperty("object_key")
        String objectKey,
        @JsonProperty("content_type")
        String contentType,
        long size,
        String checksum
    ) {
    }
}
