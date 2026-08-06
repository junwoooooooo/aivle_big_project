package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.entity.LegalApplicability;
import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.common.entity.RiskLevel;
import java.math.BigDecimal;
import java.util.List;

public record LegalReviewAiResponse(
    String provider,
    String model,
    String providerRequestId,
    RiskLevel overallRiskLevel,
    String summary,
    List<Finding> findings,
    List<Question> questions
) {
    public record Finding(
        LegalCategory category,
        LegalApplicability applicability,
        RiskLevel riskLevel,
        String title,
        String finding,
        String rationale,
        String recommendedAction,
        List<String> evidence,
        List<String> sourceSectionCodes,
        boolean requiresProfessionalReview,
        BigDecimal confidence
    ) {}
    public record Question(String question, String reason) {}
}
