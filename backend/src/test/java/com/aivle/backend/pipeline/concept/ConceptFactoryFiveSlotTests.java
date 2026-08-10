package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryCompletionPolicy;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptFingerprint;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptSlot;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptFactoryFiveSlotTests {
    @Test
    void createsExactlyOneOrderedSlotForEveryVariationFocus() {
        ConceptFactoryRun run = ConceptFactoryStateMachineTests.run();
        List<ConceptSlot> slots = Arrays.stream(VariationFocus.values())
            .map(focus -> ConceptSlot.create(run, focus.ordinal() + 1, focus))
            .toList();

        assertThat(slots).hasSize(5);
        assertThat(slots).extracting(ConceptSlot::getSlotNumber).containsExactly(1, 2, 3, 4, 5);
        assertThat(slots).extracting(ConceptSlot::getVariationFocus).containsExactly(VariationFocus.values());
    }

    @Test
    void finalPairwiseInvariantRejectsAHighConfidenceCloneBeforeCompletion() throws Exception {
        ConceptFactoryRun run = ConceptFactoryStateMachineTests.run();
        run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        List<ConceptSlot> slots = Arrays.stream(VariationFocus.values())
            .map(focus -> eligibleSlot(run, focus)).toList();
        String candidateJson = Files.readString(Path.of("../contracts/concept/business-fingerprint-v1.json"));
        var fingerprint = ConceptFingerprint.from(new ObjectMapper().readTree(candidateJson));
        List<Concept> concepts = slots.stream().map(slot -> Concept.eligible(run, slot, "clone", "clone",
            fingerprint.canonicalHash(), fingerprint.majorFieldHash(), ConceptLegalStatus.IMPLEMENTABLE,
            candidateJson, "{}")).toList();

        assertThatThrownBy(() -> ConceptFactoryCompletionPolicy.complete(run, slots, concepts, new ObjectMapper()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("FINAL_CONCEPT_SET_DUPLICATE");
        assertThat(run.getStatus()).isEqualTo(ConceptFactoryRunStatus.VALIDATING);
    }

    private ConceptSlot eligibleSlot(ConceptFactoryRun run, VariationFocus focus) {
        ConceptSlot slot = ConceptSlot.create(run, focus.ordinal() + 1, focus);
        slot.transitionTo(ConceptSlotStatus.GENERATING);
        slot.transitionTo(ConceptSlotStatus.GENERATED);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_DISTINCTNESS);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_LEGAL);
        slot.transitionTo(ConceptSlotStatus.ELIGIBLE);
        return slot;
    }
}
