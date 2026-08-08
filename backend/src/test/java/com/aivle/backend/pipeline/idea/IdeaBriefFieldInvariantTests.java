package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import org.junit.jupiter.api.Test;

class IdeaBriefFieldInvariantTests {
    @Test
    void aiCannotCreateLockedOrUserConfirmedFields() {
        IdeaBrief brief = IdeaBrief.initial(null, 7L);

        assertThatThrownBy(() -> IdeaBriefField.aiProposal(
            brief, "problem", "proposal", IdeaDecisionState.LOCKED, IdeaFieldProvenance.AI_PROPOSED
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdeaBriefField.aiProposal(
            brief, "problem", "proposal", IdeaDecisionState.OPEN, IdeaFieldProvenance.USER_CONFIRMED
        )).isInstanceOf(IllegalArgumentException.class);

        IdeaBriefField userField = IdeaBriefField.userValue(
            brief, "problem", "confirmed by user", IdeaDecisionState.LOCKED
        );
        assertThat(userField.getDecisionState()).isEqualTo(IdeaDecisionState.LOCKED);
        assertThat(userField.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_INPUT);
    }

    @Test
    void needsInputRequiresAnActionableQuestionOrMissingField() {
        IdeaBrief brief = IdeaBrief.initial(null, 7L);

        assertThatThrownBy(() -> brief.needsInput(0, 0))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("NEEDS_INPUT requires");
    }

    @Test
    void extractedCommitmentOnlyLocksAfterUserConfirmationAndCannotOverrideDirectInput() {
        IdeaBrief brief = IdeaBrief.initial(null, 7L);
        IdeaBriefField extracted = IdeaBriefField.aiProposal(
            brief, "price", "월 9,900원", IdeaDecisionState.REVIEWABLE, IdeaFieldProvenance.AI_DERIVED);
        assertThat(extracted.getDecisionState()).isEqualTo(IdeaDecisionState.REVIEWABLE);

        extracted.confirmCommitment("월 9,900원");
        assertThat(extracted.getDecisionState()).isEqualTo(IdeaDecisionState.LOCKED);
        assertThat(extracted.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_CONFIRMED);

        IdeaBriefField direct = IdeaBriefField.userValue(
            brief, "price", "월 12,000원", IdeaDecisionState.LOCKED);
        direct.confirmCommitment("월 9,900원");
        assertThat(direct.getFieldValue()).isEqualTo("월 12,000원");
        assertThat(direct.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_INPUT);
    }
}
