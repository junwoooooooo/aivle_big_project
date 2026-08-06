package com.aivle.backend.integration.ai.feasibility;

import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import java.util.List;

public record FeasibilityAnalysisAiResponse(
    String provider,
    String model,
    String providerRequestId,
    String summary,
    List<String> keyStrengths,
    List<String> keyRisks,
    List<Dimension> dimensions,
    List<ValidationTask> validationTasks
) {
    public record Dimension(
        DimensionCode code,
        Integer score,
        Confidence confidence,
        DimensionStatus status,
        String finding,
        String rationale,
        List<String> strengths,
        List<String> risks,
        List<String> assumptions,
        List<Evidence> evidence,
        List<String> sourceSectionCodes,
        List<Long> legalFindingIds,
        List<String> recommendedActions
    ) {}

    public record Evidence(
        EvidenceType type, String description, String reference
    ) {}

    public record ValidationTask(
        String code,
        DimensionCode dimensionCode,
        String title,
        String description,
        String reason,
        ValidationPriority priority,
        String validationMethod,
        String expectedEvidence
    ) {}
}
