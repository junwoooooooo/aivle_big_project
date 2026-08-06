package com.aivle.backend.integration.ai.task.dto;

import com.aivle.backend.integration.ai.task.AiTaskType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record AiTaskRequest(
    @JsonProperty("request_id")
    String requestId,
    @JsonProperty("task_id")
    String taskId,
    @JsonProperty("task_type")
    AiTaskType taskType,
    @JsonProperty("schema_version")
    String schemaVersion,
    JsonNode input,
    JsonNode context,
    JsonNode options,
    List<ArtifactInput> artifacts,
    @JsonProperty("output_targets")
    List<OutputTarget> outputTargets
) {
    public AiTaskRequest(
        String requestId,
        String taskId,
        AiTaskType taskType,
        String schemaVersion,
        JsonNode input,
        JsonNode context,
        JsonNode options
    ) {
        this(
            requestId,
            taskId,
            taskType,
            schemaVersion,
            input,
            context,
            options,
            List.of(),
            List.of()
        );
    }

    public record ArtifactInput(
        @JsonProperty("artifact_id")
        String artifactId,
        String role,
        @JsonProperty("object_key")
        String objectKey,
        @JsonProperty("download_url")
        String downloadUrl,
        @JsonProperty("content_type")
        String contentType,
        long size,
        String checksum
    ) {
    }

    public record OutputTarget(
        String role,
        @JsonProperty("object_key")
        String objectKey,
        @JsonProperty("upload_url")
        String uploadUrl,
        @JsonProperty("content_type")
        String contentType
    ) {
    }
}
