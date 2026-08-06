package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptSlot;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
