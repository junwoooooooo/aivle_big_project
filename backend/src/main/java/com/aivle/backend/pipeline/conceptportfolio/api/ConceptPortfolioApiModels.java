package com.aivle.backend.pipeline.conceptportfolio.api;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.util.List;
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
        String initialTaskRunId,
        String activeTaskRunId,
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

    public record InputRequestResponse(
        String inputRequestId,
        String candidateId,
        String lineageId,
        String candidateDisplayName,
        String candidateOneLineSummary,
        String scope,
        String status,
        String question,
        String reason,
        String safeSummary,
        JsonNode unknownFacts,
        JsonNode affectedFields,
        String nextAction,
        String continuationTaskRunId,
        Instant createdAt,
        Instant answeredAt,
        Instant resolvedAt
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SubmitInputResponseRequest(
        @NotNull JsonNode confirmedFacts,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @Size(max = 2000) String note
    ) {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown continuation response field: " + name);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RetryContinuationRequest(
        @NotBlank @Size(max = 128) String idempotencyKey
    ) {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown continuation retry field: " + name);
        }
    }

    public record ContinuationAcceptedResponse(
        String inputResponseId,
        String inputRequestId,
        String inputRequestStatus,
        String continuationTaskRunId,
        String runId,
        String activeTaskRunId
    ) { }
}
