package com.aivle.backend.analysis.legal.dto;

import com.aivle.backend.analysis.legal.entity.*;
import com.aivle.backend.common.entity.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LegalReviewResponse(
    Long legalReviewId, Long projectId, Long structuredPlanId, Long sourceDocumentVersionId,
    Integer versionNumber, LegalReviewStatus status, RiskLevel overallRiskLevel,
    String summary, String disclaimer, String provider, String modelName,
    String promptVersion, LocalDateTime completedAt, List<Finding> findings,
    List<Question> questions
) {
    public record Finding(
        Long id, LegalCategory category, Integer displayOrder,
        LegalApplicability applicability, RiskLevel riskLevel, String title,
        String finding, String rationale, String recommendedAction,
        String evidenceJson, String sourceSectionCodesJson,
        Boolean requiresProfessionalReview, BigDecimal confidence
    ) {}
    public record Question(Long id, Integer displayOrder, String question, String reason,
                           LegalQuestionStatus status) {}
}
