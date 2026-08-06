package com.aivle.backend.integration.ai.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.DimensionCode;
import com.aivle.backend.common.entity.RiskLevel;
import java.util.List;

public record FeasibilityAnalysisAiRequest(
    Long projectId,
    Long structuredPlanId,
    Long legalReviewId,
    Long sourceDocumentVersionId,
    String promptVersion,
    String catalogVersion,
    String promptText,
    List<CatalogDimension> catalog,
    List<Section> sections,
    List<Completion> completions,
    LegalContext legalReview
) {
    public record CatalogDimension(
        DimensionCode code, String displayName, int displayOrder, String description,
        List<String> sourceSectionCodes
    ) {}
    public record Section(
        String code, String title, String content, String itemStatus,
        String evidenceJson, String sourceBlockReferencesJson
    ) {}
    public record Completion(
        String fieldCode, String sectionCode, String status, String userValue, String reason
    ) {}
    public record LegalContext(
        Long legalReviewId, String status, RiskLevel overallRiskLevel, String summary,
        List<LegalFinding> findings, List<LegalQuestion> questions
    ) {}
    public record LegalFinding(
        Long id, String category, String applicability, RiskLevel riskLevel,
        String finding, String rationale, String recommendedAction
    ) {}
    public record LegalQuestion(Long id, String question, String reason, String status) {}
}
