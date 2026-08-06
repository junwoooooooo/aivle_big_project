package com.aivle.backend.pipeline.planning.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class PlanningApiModels {
    private PlanningApiModels() {}
    public record DecisionRequest(@NotBlank String action, JsonNode modifiedAfter) {}
    public record ChangeProposalView(String proposalId, String meaningfulTitle, List<String> affectedFields,
        JsonNode before, JsonNode after, String reason, JsonNode evidenceReferences, List<String> impactAreas,
        String decisionStatus, JsonNode modifiedAfter) {}
    public record FinalizedSnapshotView(String contract, String snapshotId, Long projectId,
        String sourceSelectionSnapshotId, int sequence, String displayLabel, JsonNode planning,
        JsonNode legalControls, JsonNode changeDecisions, String snapshotHash, Instant finalizedAt) {}
    public record PlanningCurrentView(String sourceSelectionSnapshotId, JsonNode selectedOriginal,
        List<ChangeProposalView> marketProposals, JsonNode appliedPreview, String appliedLabel,
        FinalizedSnapshotView finalizedPlanning, List<FinalizedSnapshotView> previousPlanning,
        boolean allDecided, boolean staleMarketResult) {}
    public record ChangeProposalListView(List<ChangeProposalView> proposals) {}
}
