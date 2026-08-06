package com.aivle.backend.pipeline.module;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectModuleStatusResponse(
    Long projectId,
    PipelineModuleType module,
    PipelineModuleStatus status,
    String statusLabelKey,
    List<String> requiredInputs,
    NextAction nextAction,
    String activeRunId,
    String sourceSnapshotId,
    LocalDateTime updatedAt
) {
    public record NextAction(String label, String route) {}
}
