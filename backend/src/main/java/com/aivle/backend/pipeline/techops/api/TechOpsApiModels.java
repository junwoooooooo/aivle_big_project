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
        @NotBlank @Size(max=255) String displayName, @NotBlank @Size(max=1000) String artifactRef,
        @Size(max=1000) String description) {}
    public record EvidenceView(String evidenceId, String evidenceType, String displayName, String artifactRef,
        String description, String source, LocalDateTime createdAt) {}
    public record PreparationView(String contract, String schemaVersion, String preparationId, Long projectId,
        String sourceMarketSeedSnapshotId, String sourceSnapshotHash, int revision, JsonNode requiredFacts,
        JsonNode proposalDecisions, List<EvidenceView> evidenceReferences, List<String> missingRequiredInputs,
        boolean readyToFinalize, String inputSnapshotId, LocalDateTime updatedAt) {}
    public record SnapshotView(String contract, String snapshotId, String schemaVersion, Long projectId,
        String preparationId, String sourceMarketSeedSnapshotId, String snapshotHash, Instant createdAt,
        JsonNode snapshot) {}
}
