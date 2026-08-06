package com.aivle.backend.pipeline.integration.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class MarketResultApiModels {
    private MarketResultApiModels() {}

    public record SourceReference(@NotBlank String title, @NotBlank String url, Instant accessedAt) {}
    public record Competitor(@NotBlank String productName, @NotBlank String companyName,
        @NotBlank String officialUrl, @NotBlank String description, @NotNull JsonNode priceEvidence,
        @NotEmpty List<@NotBlank String> keyFeatures, @NotBlank String targetCustomer,
        @NotNull Instant researchedAt, @NotEmpty List<@Valid SourceReference> sourceReferences,
        @NotBlank String verificationStatus) {}
    public record PlanningChangeProposalInput(@NotBlank String proposalId, @NotBlank String meaningfulTitle,
        @NotEmpty List<@NotBlank String> affectedFields, @NotNull JsonNode before, @NotNull JsonNode after,
        @NotBlank String reason, @NotEmpty List<@Valid SourceReference> evidenceReferences,
        @NotEmpty List<@NotBlank String> impactAreas, @NotBlank String decisionStatus) {}
    public record MarketResultIntakeRequest(@NotBlank String contract, @NotBlank String moduleRunId,
        @NotBlank String inputSnapshotId, @NotBlank String status, @NotBlank String resultReference,
        @NotNull JsonNode summary, @NotNull List<@Valid Competitor> competitors,
        @NotNull List<@Valid PlanningChangeProposalInput> planningChangeProposals,
        @NotNull Instant completedAt, @NotBlank String resultHash) {}
    public record ProposalDecisionRequest(@NotBlank String action, JsonNode modifiedAfter) {}
    public record ProposalView(String proposalId, String meaningfulTitle, List<String> affectedFields,
        JsonNode before, JsonNode after, String reason, List<SourceReference> evidenceReferences,
        List<String> impactAreas, String decisionStatus, JsonNode modifiedAfter) {}
    public record MarketResultView(String contract, String moduleRunId, String inputSnapshotId, String status,
        boolean stale, String resultReference, JsonNode summary, List<Competitor> competitors,
        List<ProposalView> planningChangeProposals, Instant completedAt, String resultHash) {}
}
