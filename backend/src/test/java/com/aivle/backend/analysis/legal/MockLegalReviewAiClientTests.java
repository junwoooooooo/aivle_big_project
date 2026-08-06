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
        assertThat(result.toString()).doesNotContain("법 제", "시행령");
    }

    private LegalReviewAiRequest request() {
        return new LegalReviewAiRequest(
            1L, 2L, 3L, LegalReviewPolicy.PROMPT_VERSION,
            LegalReviewPolicy.PROMPT,
            List.of(new LegalReviewAiRequest.Section(
                "LEGAL_PERMITS", "법률 및 인허가", "확인 필요", "[]")));
    }
}
