package com.aivle.backend.analysis.feasibility;

import com.aivle.backend.analysis.feasibility.application.FeasibilityScorePolicy;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.RiskLevel;
import com.aivle.backend.integration.ai.feasibility.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityDomainTests {
    @Test
    void catalogHasTenStableOrderedWeightedDimensions() {
        var catalog = FeasibilityDimensionCatalog.all();
        assertThat(catalog).hasSize(10);
        assertThat(catalog).extracting(FeasibilityDimensionCatalog.Definition::code)
            .containsExactly(DimensionCode.values());
        assertThat(catalog).extracting(FeasibilityDimensionCatalog.Definition::displayOrder)
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(catalog.stream().mapToInt(
            FeasibilityDimensionCatalog.Definition::weight).sum()).isEqualTo(100);
    }

    @Test
    void missingScoreIsNotZeroAndMakesOverallInsufficient() {
        var dimensions = dimensions(70);
        dimensions.set(2, dimension(DimensionCode.MARKET_ATTRACTIVENESS, null, Confidence.LOW));
        var result = new FeasibilityScorePolicy().evaluate(dimensions, List.of(), RiskLevel.LOW);
        assertThat(result.overallScore()).isNull();
        assertThat(result.verdict()).isEqualTo(Verdict.INSUFFICIENT_INFORMATION);
        assertThat(result.status()).isEqualTo(AssessmentStatus.NEEDS_VALIDATION);
    }

    @Test
    void weightedScoreIsNotSimpleAverageAndHighLegalRiskCapsVerdict() {
        var dimensions = dimensions(80);
        dimensions.set(0, dimension(DimensionCode.PROBLEM_AND_NEED, 50, Confidence.MEDIUM));
        var result = new FeasibilityScorePolicy().evaluate(dimensions, List.of(), RiskLevel.HIGH);
        assertThat(result.overallScore()).isEqualTo(76);
        assertThat(result.verdict()).isEqualTo(Verdict.CONDITIONAL);
    }

    private List<FeasibilityAnalysisAiResponse.Dimension> dimensions(int score) {
        return new ArrayList<>(Arrays.stream(DimensionCode.values())
            .map(code -> dimension(code, score, Confidence.MEDIUM)).toList());
    }

    private FeasibilityAnalysisAiResponse.Dimension dimension(
        DimensionCode code, Integer score, Confidence confidence
    ) {
        return new FeasibilityAnalysisAiResponse.Dimension(
            code, score, confidence,
            score == null ? DimensionStatus.INSUFFICIENT_INFORMATION : DimensionStatus.ASSESSED,
            "finding", "rationale", List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of());
    }
}
