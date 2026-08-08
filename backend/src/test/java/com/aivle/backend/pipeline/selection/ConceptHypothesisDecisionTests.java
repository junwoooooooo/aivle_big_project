package com.aivle.backend.pipeline.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.selection.domain.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConceptHypothesisDecisionTests {
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void rejectCreatesAUsableAlternativeProposalInsteadOfADeadEnd() {
        ConceptHypothesisDecision current = open(HypothesisType.REVENUE_MODEL, "\"월 구독\"");

        current.reject();
        ConceptHypothesisDecision alternative = ConceptHypothesisDecision.alternative(
            current, "\"거래 수수료\"", 7L);

        assertThat(current.getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.REJECTED);
        assertThat(alternative.getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.ALTERNATIVE_PROPOSED);
        assertThat(alternative.getProposalVersion()).isEqualTo(2);
        assertThat(alternative.accepted()).isFalse();
    }

    @Test
    void lockedSeedValueCannotBeMutatedThroughHypothesisDecision() {
        ConceptHypothesisDecision locked = ConceptHypothesisDecision.initial(selection(), HypothesisType.PRICE,
            "\"월 9,900원\"", "USER_INPUT", true, 7L, NOW);

        assertThatThrownBy(locked::reject).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> locked.accept("\"월 19,900원\"", true, 7L, NOW, true, true, "{}"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revenueChangeRequiresPassedDeltaReview() {
        ConceptHypothesisDecision revenue = open(HypothesisType.REVENUE_MODEL, "\"월 구독\"");

        revenue.accept("\"거래 수수료\"", true, 7L, NOW, true, false, "{\"status\":\"REJECTED\"}");

        assertThat(revenue.accepted()).isFalse();
        assertThat(revenue.getFinalValueJson()).isNull();
        assertThat(revenue.getLegalReviewStatus()).isEqualTo(HypothesisLegalReviewStatus.FAILED);
    }

    @Test
    void passedRevenueDeltaReviewAllowsAcceptance() {
        ConceptHypothesisDecision revenue = open(HypothesisType.REVENUE_MODEL, "\"월 구독\"");

        revenue.accept("\"거래 수수료\"", true, 7L, NOW, true, true,
            "{\"status\":\"IMPLEMENTABLE_WITH_CONTROLS\"}");

        assertThat(revenue.accepted()).isTrue();
        assertThat(revenue.getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.USER_EDITED_ACCEPTED);
        assertThat(revenue.getLegalReviewStatus()).isEqualTo(HypothesisLegalReviewStatus.PASSED);
    }

    @Test
    void somChangeIsAcceptedWithoutDeltaReview() {
        ConceptHypothesisDecision som = open(HypothesisType.PRE_MARKET_SOM, "{\"amount\":100000000}");

        som.accept("{\"amount\":200000000}", true, 7L, NOW, true, false, null);

        assertThat(som.accepted()).isTrue();
        assertThat(som.getLegalImpact()).isEqualTo(HypothesisLegalImpact.NON_LEGAL);
        assertThat(som.getLegalReviewStatus()).isEqualTo(HypothesisLegalReviewStatus.NOT_REQUIRED);
    }

    private ConceptHypothesisDecision open(HypothesisType type, String proposed) {
        return ConceptHypothesisDecision.initial(selection(), type, proposed, "AI_HYPOTHESIS", false, 7L, NOW);
    }

    private ConceptSelection selection() {
        return ConceptSelection.select(41L, "concept-1", "선택 이유",
            "sha256:" + "a".repeat(64), 7L, NOW);
    }
}
