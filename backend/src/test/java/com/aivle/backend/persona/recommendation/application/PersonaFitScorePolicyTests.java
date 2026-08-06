package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.integration.ai.persona.PersonaRecommendationAiResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;
import static org.assertj.core.api.Assertions.*;

class PersonaFitScorePolicyTests {
    private final PersonaFitScorePolicy policy = new PersonaFitScorePolicy();

    @Test
    void assignsPrimarySecondaryAndLowPriorityWithoutProbabilitySemantics() {
        var result = policy.evaluate(List.of(item("P1", 1, 70, PersonaConfidence.HIGH),
            item("P2", 2, 60, PersonaConfidence.MEDIUM),
            item("P3", 3, null, PersonaConfidence.LOW)));
        assertThat(result.primaryPersonaCode()).isEqualTo("P1");
        assertThat(result.secondaryPersonaCode()).isEqualTo("P2");
        assertThat(result.confidence()).isEqualTo(PersonaConfidence.LOW);
        assertThat(result.levels()).containsEntry("P1", RecommendationLevel.PRIMARY)
            .containsEntry("P2", RecommendationLevel.SECONDARY)
            .containsEntry("P3", RecommendationLevel.INSUFFICIENT_INFORMATION);
    }

    @Test
    void rejectsDuplicateCodesRanksAndOutOfRangeScores() {
        assertThatThrownBy(() -> policy.evaluate(List.of(
            item("P1", 1, 101, PersonaConfidence.LOW),
            item("P1", 1, 50, PersonaConfidence.LOW))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private PersonaRecommendationAiResponse.Item item(
        String code, int rank, Integer score, PersonaConfidence confidence
    ) {
        return new PersonaRecommendationAiResponse.Item(
            code, rank, score, confidence, List.of(), List.of(), List.of(),
            List.of(), List.of(), "가설 해석");
    }
}
