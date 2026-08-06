package com.aivle.backend.integration.ai.persona;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MockPersonaRecommendationAiClientTests {
    private final MockPersonaRecommendationAiClient client =
        new MockPersonaRecommendationAiClient();

    @Test
    void createsDeterministicHypothesesAndQuestionsWithoutFabricatedAnswers() {
        var request = request();
        var first = client.analyze(request);
        var second = client.analyze(request);
        assertThat(first).isEqualTo(second);
        assertThat(first.provider()).isEqualTo("mock");
        assertThat(first.items()).hasSize(3)
            .extracting(PersonaRecommendationAiResponse.Item::personaCode)
            .containsOnly("P1", "P2", "P3");
        assertThat(first.items()).extracting(PersonaRecommendationAiResponse.Item::rank)
            .containsExactly(1, 2, 3);
        assertThat(first.validationPlans()).hasSize(2).allSatisfy(plan -> {
            assertThat(plan.suggestedSampleSize()).isNull();
            assertThat(plan.interviewQuestions()).hasSizeBetween(5, 10)
                .allSatisfy(question -> assertThat(question.avoidLeading()).isTrue());
            assertThat(plan.surveyQuestions()).hasSize(5);
            assertThat(plan.linkedFeasibilityTaskIds()).containsExactly(71L);
        });
        assertThat(first.toString()).doesNotContain(
            "가상 응답", "구매확률", "시장점유율", "응답률");
    }

    private PersonaRecommendationAiRequest request() {
        return new PersonaRecommendationAiRequest(
            1L, 2L, 3L, 4L, "persona-recommendation-v1",
            "persona-catalog-v1", "prompt",
            List.of(new PersonaRecommendationAiRequest.Section(
                "TARGET_CUSTOMER", "20대 디지털 구독 고객", "CONFIRMED", "DOCUMENT")),
            new PersonaRecommendationAiRequest.FeasibilityContext(
                "CONDITIONAL", 60, "LOW", List.of(),
                List.of(new PersonaRecommendationAiRequest.ValidationTask(
                    71L, "VERIFY_CUSTOMER", "TARGET_CUSTOMER", "고객 검증",
                    "근거 부족", "HIGH", "INTERVIEW", "과거 행동")),
                "MEDIUM"),
            List.of(persona("P1", "20대 디지털 구독형"),
                persona("P2", "30대 온라인 탐색형"),
                persona("P3", "40대 오프라인 안정형")));
    }

    private PersonaRecommendationAiRequest.BaselinePersona persona(
        String code, String name
    ) {
        return new PersonaRecommendationAiRequest.BaselinePersona(
            code, code, name, "20대", "여", null, new BigDecimal("0.2500"),
            "2025 한국미디어패널조사", "p25v32", "persona-catalog-v1",
            "[\"디지털\",\"구독\"]", "{}", "[]", "[]");
    }
}
