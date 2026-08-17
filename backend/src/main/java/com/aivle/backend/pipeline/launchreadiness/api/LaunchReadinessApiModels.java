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
        JsonNode externalEvidence, Instant completedAt, boolean current, boolean stale,
        boolean retryAvailable, String staleReason, String sourceBasis, JsonNode sourceBinding) {
        public ProfessionalAnalysisView(String moduleType, String status, boolean retryable,
                String safeErrorCode, String taskRunId, String jobId, String inputSnapshotId,
                String sourceDocumentName, JsonNode professionalInput, String sourceDocumentHash,
                String inputSnapshotHash, String resultId, String resultHash, JsonNode analysis,
                JsonNode quality, JsonNode externalEvidence, Instant completedAt,
                boolean current, boolean stale) {
            this(moduleType, status, retryable, safeErrorCode, taskRunId, jobId, inputSnapshotId,
                sourceDocumentName, professionalInput, sourceDocumentHash, inputSnapshotHash,
                resultId, resultHash, analysis, quality, externalEvidence, completedAt,
                current, stale, retryable && !stale, stale ? "HISTORICAL_INPUT" : null,
                "PROFESSIONAL_INPUT", null);
        }
    }
    public record LaunchReadinessSummary(ProfessionalAnalysisView technology,
        ProfessionalAnalysisView operations, JsonNode finance) {}
}
