package com.aivle.backend.pipeline.conceptportfolio.selection.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class ConceptPortfolioSelectionApiModels {
    private ConceptPortfolioSelectionApiModels() { }
    public record CreateSelectionRequest(
        @NotBlank @Size(max=64) String runId,
        @NotBlank @Size(max=64) String conceptId,
        @NotBlank @Size(max=1000) String selectionReason,
        @NotBlank @Size(max=128) String idempotencyKey) { }
    public record ConfirmHypothesesRequest(
        @NotNull JsonNode changes,
        @AssertTrue boolean confirmAll,
        @NotBlank @Size(max=128) String idempotencyKey) { }
    public record ActionRequest(@NotBlank @Size(max=128) String idempotencyKey) { }
    public record SelectionView(Long selectionId, String runId, String conceptId, String conceptName,
        String status, int hypothesisCount, int hypothesisConfirmedCount, boolean deltaLegalRequired,
        String deltaLegalStatus, String legalReportStatus, String marketSeedStatus,
        String activeTaskRunId, String nextAction, Instant updatedAt) { }
    public record HypothesisView(String id, String hypothesisType, JsonNode proposedValue,
        JsonNode finalValue, String source, String decisionStatus, int proposalVersion,
        boolean locked, String semanticStatus, String semanticReason, String legalImpact,
        String legalReviewStatus, boolean deltaLegalRequired, Instant decidedAt) { }
    public record ActionAccepted(Long selectionId, String action, String taskRunId, String status) { }
    public record LegalReportView(String reportId, Long selectionId, String conceptId, String status,
        String schemaVersion, String reportHash, java.time.LocalDate basisDate, java.time.LocalDateTime generatedAt,
        JsonNode report) { }
    public record MarketSeedView(String contract, String snapshotId, String schemaVersion,
        Long projectId, Long portfolioSelectionId, String portfolioConceptId, String legalReportId,
        String sourceSnapshotHash, String snapshotHash, Instant createdAt, JsonNode snapshot) { }
}
