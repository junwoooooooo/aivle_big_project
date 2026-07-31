package com.aivle.backend.analysis.legal;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.application.LegalReviewPolicy;
import com.aivle.backend.integration.ai.legal.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MockLegalReviewAiClientTests {
    private final MockLegalReviewAiClient client = new MockLegalReviewAiClient();

    @Test
    void returnsEveryCategoryOnceWithExplicitMockProvenance() {
        var result = client.review(request());
        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.model()).isEqualTo("mock-legal-review-v1");
        assertThat(result.findings()).extracting(LegalReviewAiResponse.Finding::category)
            .containsExactly(LegalCategory.values());
    }

    @Test
    void exposesUncertaintyActionsQuestionsAndNoFabricatedStatute() {
        var result = client.review(request());
        assertThat(result.questions()).isNotEmpty();
        assertThat(result.findings())
            .allSatisfy(item -> {
                assertThat(item.recommendedAction()).isNotBlank();
                assertThat(item.rationale()).contains("달라질 수");
            });
        // Mock은 조문 원문을 지어내지 않는다. 축자 검증되지 않은 법령 문구가 화면 캡처만으로
        // 실제 조문처럼 유통되면 안 되므로, 근거는 법령명·조문번호·쉬운 설명·법제처 링크까지만 싣는다.
        assertThat(result.findings())
            .flatExtracting(LegalReviewAiResponse.Finding::evidence)
            .allSatisfy(item -> {
                assertThat(item.excerpt()).isNull();
                assertThat(item.plainSummary()).isNotBlank();
                assertThat(item.lawUrl()).startsWith("https://www.law.go.kr/");
            });
        assertThat(result.toString()).doesNotContain("시행령");
    }

    @Test
    void categoriesGetDistinctEvidenceAndReasoning() {
        var result = client.review(request());
        var withStory = result.findings().stream()
            .filter(item -> !item.evidence().isEmpty()).toList();
        // 이전 Mock은 9개 범주가 같은 문구를 써서 화면이 전부 똑같아 보였다.
        assertThat(withStory).hasSizeGreaterThan(5);
        // 조문 번호는 법령이 다르면 겹친다(전자상거래법 제17조 / 근로기준법 제17조) — 법령까지 묶어 본다
        assertThat(withStory)
            .extracting(item -> item.evidence().get(0).lawName() + " " + item.evidence().get(0).article())
            .doesNotHaveDuplicates();
        assertThat(withStory).allSatisfy(item -> {
            assertThat(item.reasoning()).isNotNull();
            assertThat(item.reasoning().regulatoryPath().topic()).isNotBlank();
            assertThat(item.reasoning().obligations()).isNotEmpty();
            assertThat(item.reasoning().consequence().text()).isNotBlank();
        });
    }

    private LegalReviewAiRequest request() {
        return new LegalReviewAiRequest(
            1L, 2L, 3L, LegalReviewPolicy.PROMPT_VERSION,
            LegalReviewPolicy.PROMPT,
            List.of(new LegalReviewAiRequest.Section(
                "LEGAL_PERMITS", "법률 및 인허가", "확인 필요", "[]")));
    }
}
