package com.aivle.backend.pipeline.concept.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

public final class ConceptFactoryCompletionPolicy {
    private ConceptFactoryCompletionPolicy() {}

    public static void complete(ConceptFactoryRun run, List<ConceptSlot> slots, List<Concept> concepts,
            ObjectMapper mapper) {
        if (run.getStatus() != ConceptFactoryRunStatus.VALIDATING) throw new IllegalStateException("run must be validating");
        if (slots.size() != ConceptFactoryLimits.SLOT_COUNT || concepts.size() != ConceptFactoryLimits.SLOT_COUNT) {
            throw new IllegalStateException("exactly five slots and concepts are required");
        }
        Set<Integer> slotNumbers = new HashSet<>();
        Set<String> conceptSlotIds = new HashSet<>();
        for (ConceptSlot slot : slots) {
            if (!slot.getRun().getId().equals(run.getId()) || slot.getStatus() != ConceptSlotStatus.ELIGIBLE) {
                throw new IllegalStateException("all slots must belong to the run and be eligible");
            }
            slotNumbers.add(slot.getSlotNumber());
        }
        if (!slotNumbers.equals(Set.of(1, 2, 3, 4, 5))) throw new IllegalStateException("slot set must be exactly 1..5");
        for (int i = 0; i < concepts.size(); i++) {
            Concept concept = concepts.get(i);
            if (!concept.getRun().getId().equals(run.getId())
                || !concept.getSourceIdeaBriefSnapshotId().equals(run.getSourceIdeaBriefSnapshotId())
                || !concept.getSourceSnapshotHash().equals(run.getSourceSnapshotHash())
                || !concept.getLegalStatus().isPubliclyEligible()) {
                throw new IllegalStateException("concept is not eligible for this snapshot");
            }
            if (!conceptSlotIds.add(concept.getSlot().getId())) throw new IllegalStateException("each slot must expose one concept");
            for (int j = i + 1; j < concepts.size(); j++) {
                Concept other = concepts.get(j);
                var left = mapper.readTree(concept.getCandidateJson());
                var right = mapper.readTree(other.getCandidateJson());
                if (ConceptFingerprint.classify(left, right, concept.getSlot().getVariationFocus())
                        == ConceptFingerprint.Classification.DUPLICATE
                    || ConceptFingerprint.classify(left, right, other.getSlot().getVariationFocus())
                        == ConceptFingerprint.Classification.DUPLICATE) {
                    throw new IllegalStateException("FINAL_CONCEPT_SET_DUPLICATE");
                }
            }
        }
        run.transitionTo(ConceptFactoryRunStatus.COMPLETED);
        concepts.forEach(Concept::publish);
    }
}
