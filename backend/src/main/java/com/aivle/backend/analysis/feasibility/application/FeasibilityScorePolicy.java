package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.analysis.feasibility.FeasibilityDimensionCatalog;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.RiskLevel;
import com.aivle.backend.integration.ai.feasibility.FeasibilityAnalysisAiResponse;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class FeasibilityScorePolicy {
    public Outcome evaluate(
        List<FeasibilityAnalysisAiResponse.Dimension> dimensions,
        List<FeasibilityAnalysisAiResponse.ValidationTask> tasks,
        RiskLevel legalRisk
    ) {
        if (dimensions.stream().anyMatch(item -> item.score() == null)) {
            return new Outcome(null, Confidence.LOW, Verdict.INSUFFICIENT_INFORMATION,
                AssessmentStatus.NEEDS_VALIDATION);
        }
        int weighted = dimensions.stream().mapToInt(item ->
            item.score() * FeasibilityDimensionCatalog.get(item.code()).weight()).sum();
        int score = Math.round(weighted / 100.0f);
        boolean severeDimension = dimensions.stream().anyMatch(item -> item.score() < 40);
        boolean legalConstraint = legalRisk == RiskLevel.HIGH || legalRisk == RiskLevel.CRITICAL;
        Verdict verdict;
        if (severeDimension || score < 50) verdict = Verdict.HIGH_RISK;
        else if (score < 75 || legalConstraint || !tasks.isEmpty()) verdict = Verdict.CONDITIONAL;
        else verdict = Verdict.PROMISING;
        Confidence confidence = dimensions.stream().allMatch(
            item -> item.confidence() == Confidence.HIGH) ? Confidence.HIGH
            : dimensions.stream().anyMatch(item -> item.confidence() == Confidence.LOW)
                ? Confidence.LOW : Confidence.MEDIUM;
        AssessmentStatus status = tasks.isEmpty()
            ? AssessmentStatus.COMPLETED : AssessmentStatus.NEEDS_VALIDATION;
        return new Outcome(score, confidence, verdict, status);
    }

    public record Outcome(
        Integer overallScore, Confidence confidence, Verdict verdict, AssessmentStatus status
    ) {}
}
