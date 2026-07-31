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
    List<Question> questions,
    ReviewMode mode, List<String> rerunCategories, List<String> carriedCategories,
    Diff diff,
    List<RevisionRequestItem> revisionRequests,
    Cycle cycle
) {
    public record Finding(
        Long id, LegalCategory category, Integer displayOrder,
        LegalApplicability applicability, RiskLevel riskLevel, String title,
        String finding, String rationale, String recommendedAction,
        String evidenceJson, String reasoningJson, String sourceSectionCodesJson,
        Boolean requiresProfessionalReview, BigDecimal confidence,
        Boolean carried
    ) {}
    public record Question(Long id, Integer displayOrder, String question, String reason,
                           LegalQuestionStatus status) {}
    public record Diff(int resolved, int added, int maintained) {}
    public record RevisionRequestItem(
        Long id, LegalCategory category, String anchorSectionCode, String anchorQuote,
        String rationale, String status, Long acceptedSuggestionId, Integer resolvedInVersion,
        List<SuggestionItem> suggestions
    ) {}
    public record SuggestionItem(Long id, String label, String newText) {}
    public record Cycle(Long cycleId, String status, Integer currentVersionNumber, boolean canPublish) {}
}
