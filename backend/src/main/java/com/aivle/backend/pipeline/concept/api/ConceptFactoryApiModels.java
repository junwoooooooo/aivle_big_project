package com.aivle.backend.pipeline.concept.api;

import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class ConceptFactoryApiModels {
    private ConceptFactoryApiModels() {}

    public record CreateRunRequest(@NotBlank String ideaBriefSnapshotId) {}
    public record RunResponse(
        String runId, String sourceIdeaBriefSnapshotId, String sourceSnapshotHash,
        ConceptFactoryRunStatus status, int replacementRounds, int inspectedCandidateCount,
        int providerTransientRetryCount, LocalDateTime updatedAt
    ) {}
    public record SlotResponse(int slotNumber, VariationFocus variationFocus, ConceptSlotStatus status, int attemptCount, int legalRedesignCount) {}
    public record ConceptResponse(String conceptId, int slotNumber, VariationFocus variationFocus, String title, String summary, ConceptLegalStatus legalStatus) {}
    public record ConceptListResponse(String runId, List<ConceptResponse> concepts) {}
}
