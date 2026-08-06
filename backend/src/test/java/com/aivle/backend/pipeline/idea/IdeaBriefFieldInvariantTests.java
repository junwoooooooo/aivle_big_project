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
        assertThat(userField.getProvenance()).isEqualTo(IdeaFieldProvenance.USER_CONFIRMED);
    }
}
