package com.aivle.backend.pipeline.integration.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class IntegrationApiModels {
    private IntegrationApiModels() {}
    public record CreateHandoffRequest(@NotBlank String module, String inputSnapshotId, String requestedOperation) {}
    public record CallbackView(String mode, String reference) {}
    public record HandoffResponse(String contract, String handoffId, Long projectId, String module, String inputSnapshotId,
                                  String inputSnapshotHash, String inputSnapshotType, String inputSchemaVersion,
                                  Instant requestedAt, CallbackView callback,
                                  String requestedOperation, String status, JsonNode input, ModuleRunResponse moduleRun) {}
    public record ModuleRunResponse(String runId, String handoffId, String module, String inputSnapshotId,
                                    String inputSnapshotHash, String status, boolean stale, boolean cancelRequested,
                                    String externalRunReference, Instant startedAt, Instant completedAt,
                                    String resultReference, String resultHash, String safeErrorCode) {}
    public record ModuleRunListResponse(List<ModuleRunResponse> runs) {}
}
