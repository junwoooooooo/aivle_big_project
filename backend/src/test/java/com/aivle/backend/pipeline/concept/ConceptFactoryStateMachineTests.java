package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlot;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.project.entity.Project;
import org.junit.jupiter.api.Test;

class ConceptFactoryStateMachineTests {
    @Test
    void acceptsOnlyBoundedForwardTransitions() {
        ConceptFactoryRun run = run();
        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        assertThat(run.getStatus()).isEqualTo(ConceptFactoryRunStatus.VALIDATING);
        assertThatThrownBy(() -> run.transitionTo(ConceptFactoryRunStatus.GENERATING))
            .isInstanceOf(IllegalStateException.class);

        ConceptSlot slot = ConceptSlot.create(run, 1, VariationFocus.CUSTOMER_EXPERIENCE);
        slot.transitionTo(ConceptSlotStatus.GENERATING);
        slot.transitionTo(ConceptSlotStatus.GENERATED);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_DISTINCTNESS);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_LEGAL);
        slot.transitionTo(ConceptSlotStatus.ELIGIBLE);
        assertThatThrownBy(() -> slot.transitionTo(ConceptSlotStatus.GENERATING))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void needsInputIsTerminalAndReplacementRoundIsSharedAcrossSlots() {
        ConceptFactoryRun needsInput = run();
        needsInput.transitionTo(ConceptFactoryRunStatus.GENERATING);
        needsInput.transitionTo(ConceptFactoryRunStatus.NEEDS_INPUT);
        assertThat(needsInput.isTerminal()).isTrue();

        ConceptFactoryRun replacing = run();
        replacing.transitionTo(ConceptFactoryRunStatus.GENERATING);
        replacing.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        replacing.ensureReplacementRound(1);
        replacing.transitionTo(ConceptFactoryRunStatus.GENERATING);
        replacing.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        replacing.ensureReplacementRound(1);
        assertThat(replacing.getReplacementRounds()).isOne();
        assertThat(replacing.getStatus()).isEqualTo(ConceptFactoryRunStatus.VALIDATING);
    }

    static ConceptFactoryRun run() {
        return ConceptFactoryRun.create(Project.create(null, "p", null, null), "brief-1", "sha256:" + "a".repeat(64), 1L);
    }
}
