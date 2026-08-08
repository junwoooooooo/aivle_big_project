package com.aivle.backend.pipeline.selection.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class SelectionApiModels {
    private SelectionApiModels() {}

    public record CreateSelectionRequest(@NotBlank String conceptId,
                                         @NotBlank @Size(max = 2000) String selectionReason) {}

    public enum HypothesisAction { ACCEPT, EDIT_AND_ACCEPT, REQUEST_ALTERNATIVE }

    public record HypothesisActionRequest(@NotNull HypothesisAction action,
                                          @Min(1) int expectedProposalVersion,
                                          JsonNode value) {}

    public record HypothesisDecisionResponse(String decisionId, String hypothesisType, JsonNode proposedValue,
        String source, String decisionStatus, JsonNode finalValue, int proposalVersion, boolean locked,
        String legalImpact, String legalReviewStatus, Instant decidedAt) {}

    public record SelectionResponse(Long selectionId, String conceptId, String selectionReason, Instant selectedAt,
                                    boolean current, boolean decisionComplete,
                                    List<HypothesisDecisionResponse> hypotheses,
                                    String activeActionTaskRunId, String pendingActionType,
                                    String pendingHypothesisType, String actionStatus, String safeActionError) {}

    public record HypothesisActionResponse(HypothesisDecisionResponse hypothesis, boolean decisionComplete,
        String taskRunId, String jobId, String status, String actionType,
        String hypothesisType, int proposalVersion) {}
}
