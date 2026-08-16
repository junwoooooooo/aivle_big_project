package com.aivle.backend.pipeline.finance.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class FinancialApiModels {
    private FinancialApiModels() {}
    public record FinancialFieldsPatch(@NotNull JsonNode values) {}
    public record EstimateDecisionRequest(@NotBlank String action, JsonNode value) {}
    public record PreparationView(String contract, String schemaVersion, String preparationId, Long projectId,
        String sourceTechOpsSnapshotId, String sourceMarketSeedSnapshotId, Long sourceMarketResearchVersionId,
        Long sourceBusinessModelVersionId, String sourceSnapshotHash, boolean stale,
        int revision, JsonNode financialFields, JsonNode upstreamReferences, JsonNode assistance,
        JsonNode calculatedCac, List<String> missingRequiredInputs, boolean readyToFinalize,
        String inputSnapshotId, LocalDateTime updatedAt) {}
    public record EstimateActionResponse(PreparationView preparation, String taskRunId, String jobId,
        String status, String actionType, String fieldKey, int proposalVersion) {}
    public record SnapshotView(String contract, String snapshotId, String schemaVersion, Long projectId,
        String preparationId, String sourceTechOpsSnapshotId, String sourceMarketSeedSnapshotId,
        Long sourceMarketResearchVersionId, Long sourceBusinessModelVersionId,
        String snapshotHash, Instant createdAt, JsonNode snapshot, boolean stale) {}
    public record DocumentImportResponse(PreparationView preparation, SnapshotView snapshot,
        AnalysisActionResponse analysis) {}
    public record AnalysisActionResponse(String taskRunId, String jobId, String status,
        String snapshotId, String snapshotHash) {}
    public record AnalysisView(String taskRunId, String jobId, String status, boolean retryable,
        String safeErrorCode, String snapshotId, String snapshotHash, JsonNode result,
        boolean fallback, boolean stale, LocalDateTime completedAt, String sourceDocumentName,
        JsonNode sourceBinding, String staleReason) {
        public AnalysisView(String taskRunId, String jobId, String status, boolean retryable,
                String safeErrorCode, String snapshotId, String snapshotHash, JsonNode result,
                boolean fallback, boolean stale, LocalDateTime completedAt, String sourceDocumentName) {
            this(taskRunId, jobId, status, retryable, safeErrorCode, snapshotId, snapshotHash,
                result, fallback, stale, completedAt, sourceDocumentName, null,
                stale ? "HISTORICAL_INPUT" : null);
        }
    }
}
