package com.aivle.backend.pipeline.concept.api;

import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class ConceptFactoryApiModels {
    private ConceptFactoryApiModels() {}

    public record CreateRunRequest(@NotBlank String ideaBriefSnapshotId) {}
    public record RetryRunRequest(@NotBlank String idempotencyKey) {}
    public record RunResponse(
        String runId, String sourceIdeaBriefSnapshotId, String sourceSnapshotHash,
        ConceptFactoryRunStatus status, int replacementRounds, int inspectedCandidateCount,
        int providerTransientRetryCount, String activeJobId,
        int eligibleCount, int initialCandidateSuccessCount, int generatedCandidateCount,
        int candidateGenerationFailureCount,
        int redesignCount, int replacementCandidateCount, long discardedCandidateCount,
        String failureScope, String failureCode, boolean retryable, boolean canResume,
        boolean canStartNew, String nextAction, List<RequiredInput> requiredInputs, Instant updatedAt
    ) {}
    public record RequiredInput(String code, String question, String source, int candidateSlot) {}
    public record SlotResponse(int slotNumber, VariationFocus variationFocus, ConceptSlotStatus status,
        String currentAttemptPhase, int candidateCount, int legalReviewAttemptCount,
        int legalRedesignCount, int replacementCount, String latestFailurePhase,
        String latestFailureCode, boolean retryable, boolean candidatePreserved, Instant updatedAt) {}
    public record EvidenceView(String sourceType, String lawId, String lawName, String articleReference,
        String title, String effectiveDate, Instant retrievedAt, String officialSourceUri) {}
    public record LegalReviewView(ConceptLegalStatus status, String safeSummary, JsonNode assessment, List<EvidenceView> evidence) {}
    public record ConceptResponse(String conceptId, int slotNumber, VariationFocus variationFocus, String title,
        String summary, ConceptLegalStatus legalStatus, String sourceSnapshotHash, String canonicalHash,
        String majorFieldHash, boolean stale, JsonNode candidate, LegalReviewView legalReview) {}
    public record ConceptListResponse(String runId, List<ConceptResponse> concepts) {}
}
