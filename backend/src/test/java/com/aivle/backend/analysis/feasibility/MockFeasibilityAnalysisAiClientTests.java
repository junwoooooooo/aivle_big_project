package com.aivle.backend.analysis.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.AnalysisType;
import com.aivle.backend.common.entity.RiskLevel;
import com.aivle.backend.integration.ai.feasibility.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MockFeasibilityAnalysisAiClientTests {
    @Test
    void returnsTenDimensionsTasksAndExplicitMockProvenanceWithoutMarketNumbers() {
        var result = new MockFeasibilityAnalysisAiClient().analyze(request());
        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.dimensions()).extracting(
            FeasibilityAnalysisAiResponse.Dimension::code).containsExactly(DimensionCode.values());
        assertThat(result.validationTasks()).extracting(
            FeasibilityAnalysisAiResponse.ValidationTask::code)
            .contains("VERIFY_MARKET_SOURCES", "VALIDATE_FINANCIAL_ASSUMPTIONS",
                "RESOLVE_LEGAL_CONSTRAINTS");
        assertThat(result.toString())
            .doesNotContain("TAM", "SAM", "SOM", "CAC", "LTV", "1.2조", "손익분기 12");
    }

    @Test
    void eachGroupGetsItsOwnNarrativeAndNoScores() {
        var result = new MockFeasibilityAnalysisAiClient().analyze(request());
        assertThat(result.groups()).extracting(FeasibilityAnalysisAiResponse.Group::analysisType)
            .containsExactly(AnalysisType.MARKET, AnalysisType.BUSINESS_MODEL,
                AnalysisType.TECHNOLOGY_OPERATION);
        // 묶음마다 문구가 달라야 화면에서 3분할이 의미를 갖는다
        assertThat(result.groups()).extracting(FeasibilityAnalysisAiResponse.Group::headline)
            .doesNotHaveDuplicates();
        assertThat(result.groups()).allSatisfy(group -> {
            assertThat(group.headline()).isNotBlank();
            assertThat(group.summary()).isNotBlank();
            assertThat(group.nextFocus()).isNotBlank();
            assertThat(group.keyStrengths()).isNotEmpty();
            assertThat(group.keyRisks()).isNotEmpty();
        });
    }

    private FeasibilityAnalysisAiRequest request() {
        var catalog = FeasibilityDimensionCatalog.all().stream().map(item ->
            new FeasibilityAnalysisAiRequest.CatalogDimension(
                item.code(), item.group(), item.displayName(), item.displayOrder(),
                item.description(),
                item.sourceSections().stream().map(Enum::name).toList())).toList();
        return new FeasibilityAnalysisAiRequest(
            1L, 2L, 3L, 4L, FeasibilityPolicy.PROMPT_VERSION,
            FeasibilityDimensionCatalog.VERSION, FeasibilityPolicy.PROMPT, catalog,
            List.of(new FeasibilityAnalysisAiRequest.Section(
                "BUSINESS_OVERVIEW", "사업 개요", "문제 설명", "PRESENT", "[]", "[]")),
            List.of(), new FeasibilityAnalysisAiRequest.LegalContext(
                3L, "NEEDS_REVIEW", RiskLevel.HIGH, "확인 필요", List.of(),
                List.of(new FeasibilityAnalysisAiRequest.LegalQuestion(
                    5L, "인허가가 필요한가요?", "미확정", "OPEN"))));
    }
}
