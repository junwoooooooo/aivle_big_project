package com.aivle.backend.pipeline.integration;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.pipeline.integration.domain.PlanningChangeProposal;
import com.aivle.backend.pipeline.integration.domain.ProposalDecisionStatus;
import org.junit.jupiter.api.Test;

class PlanningChangeProposalTests {
    private PlanningChangeProposal proposal() {
        return PlanningChangeProposal.pending("proposal-1", "run-1", 1L, "초기 고객을 관리사무소 계약 단지로 좁히기",
            "[]", "{}", "{}", "근거 기반 변경", "[]", "[]");
    }
    @Test void partialAdoptionRequiresEditedValue() {
        var value = proposal();
        assertThatThrownBy(() -> value.decide(ProposalDecisionStatus.PARTIALLY_ADOPT, null))
            .isInstanceOf(IllegalArgumentException.class);
        value.decide(ProposalDecisionStatus.PARTIALLY_ADOPT, "{\"targetCustomer\":\"3개 계약 단지\"}");
        assertThat(value.getDecisionStatus()).isEqualTo(ProposalDecisionStatus.PARTIALLY_ADOPT);
        assertThat(value.getModifiedAfterJson()).contains("3개 계약 단지");
    }
    @Test void adoptAndRejectCannotCarryEditedValue() {
        assertThatThrownBy(() -> proposal().decide(ProposalDecisionStatus.ADOPT, "{}"))
            .isInstanceOf(IllegalArgumentException.class);
        proposal().decide(ProposalDecisionStatus.REJECT, null);
    }
}
