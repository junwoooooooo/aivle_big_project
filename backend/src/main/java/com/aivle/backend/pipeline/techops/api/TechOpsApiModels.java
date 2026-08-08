package com.aivle.backend.pipeline.techops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class TechOpsApiModels {
    private TechOpsApiModels() {}
    public record RequiredFactsPatch(@NotNull JsonNode values) {}
    public record ProposalDecisionRequest(@NotBlank String action, JsonNode value) {}
    public record EvidenceRequest(@NotBlank @Size(max=30) String evidenceType,
        @NotBlank @Size(max=64) String artifactId, @Size(max=1000) String description) {}
    public record EvidenceView(String evidenceId, String evidenceType, String artifactId,
        String originalFilename, String displayName, String mediaType, Long sizeBytes, String sha256,
        String description, String source, LocalDateTime createdAt) {}
    public record PreparationView(String contract, String schemaVersion, String preparationId, Long projectId,
        String sourceMarketSeedSnapshotId, String sourceSnapshotHash, int revision, JsonNode requiredFacts,
        JsonNode proposalDecisions, List<EvidenceView> evidenceReferences, List<String> missingRequiredInputs,
        boolean readyToFinalize, String inputSnapshotId, String proposalGenerationStatus,
        String activeProposalTaskRunId, String safeError, LocalDateTime updatedAt) {}
    public record ProposalActionResponse(PreparationView preparation, String taskRunId, String jobId,
        String status, String actionType, String fieldKey, int proposalVersion) {}
    public record SnapshotView(String contract, String snapshotId, String schemaVersion, Long projectId,
        String preparationId, String sourceMarketSeedSnapshotId, String snapshotHash, Instant createdAt,
        JsonNode snapshot) {}
}
