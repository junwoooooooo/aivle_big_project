package com.aivle.backend.pipeline.concept.application;

import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConceptFactoryRetryPolicy {
    private static final List<ConceptAttemptError> DOMAIN_EXHAUSTION = List.of(
        ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS,
        ConceptAttemptError.DISTINCTNESS_EXHAUSTED,
        ConceptAttemptError.REPLACEMENT_EXHAUSTED,
        ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED,
        ConceptAttemptError.LOCKED_CONSTRAINT_INVALID
    );

    public Decision evaluate(ConceptFactoryRunStatus runStatus, boolean snapshotCurrent,
            List<SlotState> slots) {
        if (!snapshotCurrent || runStatus == ConceptFactoryRunStatus.STALE) {
            return Decision.startNew("IDEA_BRIEF_SNAPSHOT_STALE");
        }
        if (runStatus != ConceptFactoryRunStatus.FAILED) {
            return new Decision(false, "WAIT", "RUN_NOT_FAILED");
        }
        boolean retryableFailure = false;
        for (SlotState slot : slots) {
            if (slot.status() == ConceptSlotStatus.ELIGIBLE || slot.status() == ConceptSlotStatus.QUEUED) continue;
            if (slot.error() == ConceptAttemptError.REQUEST_CONTRACT_INVALID) {
                return new Decision(false, "FIX_SYSTEM_AND_START_NEW_RUN", slot.error().name());
            }
            if (slot.error() == ConceptAttemptError.PERMANENT_PROVIDER_FAILURE
                    || DOMAIN_EXHAUSTION.contains(slot.error())) {
                return Decision.startNew(slot.error() == null ? "DOMAIN_EXHAUSTED" : slot.error().name());
            }
            if (slot.retryable()) retryableFailure = true;
            else if (slot.status() == ConceptSlotStatus.FAILED) {
                return Decision.startNew(slot.error() == null ? "NON_RETRYABLE_FAILURE" : slot.error().name());
            }
        }
        if (!retryableFailure) return Decision.startNew("NO_RETRYABLE_FAILURE");
        boolean preserved = slots.stream().anyMatch(SlotState::candidatePreserved);
        return new Decision(true, preserved ? "RESUME_PRESERVED_CANDIDATE" : "RESUME_GENERATION",
            "RETRYABLE_PROVIDER_OR_LEGAL_FAILURE");
    }

    public record SlotState(ConceptSlotStatus status, ConceptAttemptError error,
                            boolean retryable, boolean candidatePreserved) { }

    public record Decision(boolean canResume, String nextAction, String reason) {
        static Decision startNew(String reason) {
            return new Decision(false, "START_NEW_RUN", reason);
        }
    }
}
