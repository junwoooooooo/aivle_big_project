package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.concept.domain.ConceptAttempt;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryLimits;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlot;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import org.junit.jupiter.api.Test;

class ConceptFactoryLimitTests {
    @Test
    void enforcesAllIterationCaps() {
        ConceptFactoryRun run = ConceptFactoryStateMachineTests.run();
        for (int i = 0; i < ConceptFactoryLimits.MAX_INSPECTED_CANDIDATES; i++) run.recordCandidateInspection();
        assertThatThrownBy(run::recordCandidateInspection).isInstanceOf(IllegalStateException.class);

        run.recordProviderTransientRetry();
        assertThatThrownBy(run::recordProviderTransientRetry).isInstanceOf(IllegalStateException.class);

        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        run.beginReplacementRound();
        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        run.beginReplacementRound();
        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        assertThatThrownBy(run::beginReplacementRound).isInstanceOf(IllegalStateException.class);

        ConceptSlot slot = ConceptSlot.create(run, 1, VariationFocus.CUSTOMER_EXPERIENCE);
        ConceptAttempt.begin(slot, ConceptAttemptPhase.REDESIGN, null);
        assertThatThrownBy(() -> ConceptAttempt.begin(slot, ConceptAttemptPhase.REDESIGN, null))
            .isInstanceOf(IllegalStateException.class);
        assertThat(slot.getLegalRedesignCount()).isOne();
    }
}
