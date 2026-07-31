package com.aivle.backend.analysis.feasibility.dto;

import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.AnalysisType;
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
    List<Group> groups,
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

    /** 시장·비즈니스 모델·기술 운영 묶음 결과. 구 assessment는 비어 있다. */
    public record Group(
        Long id,
        AnalysisType analysisType,
        Integer displayOrder,
        Integer score,
        Verdict verdict,
        String headline,
        String summary,
        String strengthsJson,
        String risksJson,
        String nextFocus
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
