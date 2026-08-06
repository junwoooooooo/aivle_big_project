package com.aivle.backend.analysis.feasibility.dto;

import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import java.time.LocalDateTime;
import java.util.List;

public record FeasibilityAssessmentResponse(
    Long assessmentId,
    Long projectId,
    Long structuredPlanId,
    Long legalReviewId,
    Long sourceDocumentVersionId,
    Integer versionNumber,
    AssessmentStatus status,
    Verdict verdict,
    Integer overallScore,
    Confidence confidence,
    String summary,
    String keyStrengthsJson,
    String keyRisksJson,
    String disclaimer,
    String provider,
    String modelName,
    String promptVersion,
    String catalogVersion,
    LocalDateTime completedAt,
    List<Dimension> dimensions,
    List<ValidationTask> validationTasks
) {
    public record Dimension(
        Long id,
        DimensionCode code,
        Integer displayOrder,
        Integer score,
        Confidence confidence,
        DimensionStatus status,
        String finding,
        String rationale,
        String strengthsJson,
        String risksJson,
        String assumptionsJson,
        String evidenceJson,
        String sourceSectionCodesJson,
        String legalFindingIdsJson,
        String recommendedActionsJson
    ) {}

    public record ValidationTask(
        Long id,
        String code,
        DimensionCode dimensionCode,
        String title,
        String description,
        String reason,
        ValidationPriority priority,
        String validationMethod,
        String expectedEvidence,
        ValidationTaskStatus status,
        Integer displayOrder
    ) {}
}
