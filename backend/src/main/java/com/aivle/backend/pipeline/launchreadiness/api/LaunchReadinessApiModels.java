package com.aivle.backend.pipeline.launchreadiness.api;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class LaunchReadinessApiModels {
    private LaunchReadinessApiModels() {}
    public record AnalysisActionResponse(String taskRunId, String jobId, String status,
        String inputSnapshotId, String inputSnapshotHash) {}
    public record ProfessionalAnalysisView(String moduleType, String status, boolean retryable,
        String safeErrorCode, String taskRunId, String jobId, String inputSnapshotId,
        String sourceDocumentName, JsonNode professionalInput, String sourceDocumentHash, String inputSnapshotHash,
        String resultId, String resultHash, JsonNode analysis, JsonNode quality,
        JsonNode externalEvidence, Instant completedAt, boolean current, boolean stale) {}
    public record LaunchReadinessSummary(ProfessionalAnalysisView technology,
        ProfessionalAnalysisView operations, JsonNode finance) {}
}
