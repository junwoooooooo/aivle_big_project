package com.aivle.backend.pipeline.conceptportfolio.api;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class ConceptPortfolioApiModels {
    private ConceptPortfolioApiModels() { }

    public record CreateRunRequest(
        @NotBlank String ideaBriefSnapshotId,
        @Min(1) @Max(5) Integer maxConcepts,
        @NotBlank @Size(max = 128) String idempotencyKey
    ) {
        public int requestedMaximum() { return maxConcepts == null ? 5 : maxConcepts; }
    }

    public record RunResponse(
        String runId,
        String ideaBriefSnapshotId,
        String sourceSnapshotHash,
        ConceptPortfolioRunStatus productStatus,
        int requestedMaxConcepts,
        int producedConceptCount,
        int selectableConceptCount,
        int openInputCount,
        String taskRunId,
        String downstreamReadiness,
        String failureCode,
        String nextAction,
        Instant updatedAt
    ) { }

    public record ConceptResponse(
        String conceptId,
        String candidateId,
        String lineageId,
        int displayOrder,
        String conceptName,
        String summary,
        String legalStatus,
        String canonicalHash,
        boolean selectable,
        JsonNode candidate,
        JsonNode legalReview
    ) { }
}
