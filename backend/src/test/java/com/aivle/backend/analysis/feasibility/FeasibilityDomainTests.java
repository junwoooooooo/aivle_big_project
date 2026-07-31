package com.aivle.backend.analysis.feasibility;

import com.aivle.backend.analysis.feasibility.application.FeasibilityScorePolicy;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.AnalysisType;
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

    @Test
    void everyDimensionBelongsToExactlyOneGroupAndWeightsSplit44_44_12() {
        assertThat(FeasibilityDimensionCatalog.all())
            .allSatisfy(item -> assertThat(item.group()).isNotNull());
        assertThat(FeasibilityDimensionCatalog.byGroup(AnalysisType.MARKET)
            .size() + FeasibilityDimensionCatalog.byGroup(AnalysisType.BUSINESS_MODEL).size()
            + FeasibilityDimensionCatalog.byGroup(AnalysisType.TECHNOLOGY_OPERATION).size())
            .isEqualTo(DimensionCode.values().length);
        assertThat(FeasibilityDimensionCatalog.groupWeight(AnalysisType.MARKET)).isEqualTo(44);
        assertThat(FeasibilityDimensionCatalog.groupWeight(AnalysisType.BUSINESS_MODEL)).isEqualTo(44);
        assertThat(FeasibilityDimensionCatalog.groupWeight(AnalysisType.TECHNOLOGY_OPERATION))
            .isEqualTo(12);
    }

    /**
     * 페르소나 단계가 이 차원 코드 4개를 문자열로 하드코딩해 필터링한다
     * (PersonaJobContextService). 코드명을 바꾸면 컴파일 에러 없이 필터가 조용히
     * 빈 리스트가 되어 페르소나 입력이 비어버리므로, 여기서 존재를 못박는다.
     */
    @Test
    void personaConsumedDimensionCodesStillExist() {
        assertThat(FeasibilityDimensionCatalog.all())
            .extracting(FeasibilityDimensionCatalog.Definition::code)
            .contains(DimensionCode.TARGET_CUSTOMER, DimensionCode.MARKET_ATTRACTIVENESS,
                DimensionCode.PRODUCT_SOLUTION_FIT, DimensionCode.GO_TO_MARKET);
    }

    @Test
    void groupScoreIsNormalisedByItsOwnWeightNotTheTotal() {
        // 기술·운영 묶음은 가중치 합이 12뿐이다 — 총점 분모(100)로 나누면 12점이 되어버린다.
        var groups = new FeasibilityScorePolicy().evaluateGroups(dimensions(80), List.of());
        assertThat(groups).extracting(FeasibilityScorePolicy.GroupOutcome::score)
            .containsOnly(80);
        assertThat(groups).extracting(FeasibilityScorePolicy.GroupOutcome::group)
            .containsExactly(AnalysisType.MARKET, AnalysisType.BUSINESS_MODEL,
                AnalysisType.TECHNOLOGY_OPERATION);
    }

    @Test
    void oneUnknownDimensionOnlyBlanksItsOwnGroup() {
        var dimensions = dimensions(80);
        // 실행 역량은 기술·운영 묶음 소속 — 시장·BM 묶음 점수는 살아 있어야 한다.
        dimensions.set(8, dimension(DimensionCode.EXECUTION_CAPABILITY, null, Confidence.LOW));
        var groups = new FeasibilityScorePolicy().evaluateGroups(dimensions, List.of());
        var byGroup = groups.stream().collect(java.util.stream.Collectors.toMap(
            FeasibilityScorePolicy.GroupOutcome::group, item -> item));
        assertThat(byGroup.get(AnalysisType.MARKET).score()).isEqualTo(80);
        assertThat(byGroup.get(AnalysisType.BUSINESS_MODEL).score()).isEqualTo(80);
        assertThat(byGroup.get(AnalysisType.TECHNOLOGY_OPERATION).score()).isNull();
        assertThat(byGroup.get(AnalysisType.TECHNOLOGY_OPERATION).verdict())
            .isEqualTo(Verdict.INSUFFICIENT_INFORMATION);
    }

    @Test
    void groupVerdictUsesTheSameThresholdsAsTheOverallScore() {
        var policy = new FeasibilityScorePolicy();
        assertThat(policy.evaluateGroups(dimensions(90), List.of()))
            .allSatisfy(item -> assertThat(item.verdict()).isEqualTo(Verdict.PROMISING));
        assertThat(policy.evaluateGroups(dimensions(60), List.of()))
            .allSatisfy(item -> assertThat(item.verdict()).isEqualTo(Verdict.CONDITIONAL));
        assertThat(policy.evaluateGroups(dimensions(45), List.of()))
            .allSatisfy(item -> assertThat(item.verdict()).isEqualTo(Verdict.HIGH_RISK));
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
