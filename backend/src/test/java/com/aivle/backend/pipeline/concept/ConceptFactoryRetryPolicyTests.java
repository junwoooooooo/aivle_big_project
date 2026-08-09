package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.application.ConceptFactoryRetryPolicy;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryRetryPolicy.SlotState;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConceptFactoryRetryPolicyTests {
    private final ConceptFactoryRetryPolicy policy = new ConceptFactoryRetryPolicy();

    @Test
    void providerTransientFailureCanResumeWithoutCandidate() {
        var result = policy.evaluate(ConceptFactoryRunStatus.FAILED, true, List.of(
            new SlotState(ConceptSlotStatus.FAILED, ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true, false),
            new SlotState(ConceptSlotStatus.QUEUED, null, false, false)));
        assertThat(result.canResume()).isTrue();
        assertThat(result.nextAction()).isEqualTo("RESUME_GENERATION");
    }

    @Test
    void preservedCandidateWithLegalTransientResumesReview() {
        var result = policy.evaluate(ConceptFactoryRunStatus.FAILED, true, List.of(
            new SlotState(ConceptSlotStatus.REVIEW_RETRY_PENDING,
                ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true, true)));
        assertThat(result.canResume()).isTrue();
        assertThat(result.nextAction()).isEqualTo("RESUME_PRESERVED_CANDIDATE");
    }

    @Test
    void domainExhaustionPermanentProviderAndStaleSnapshotCannotResume() {
        assertThat(policy.evaluate(ConceptFactoryRunStatus.FAILED, true, List.of(
            new SlotState(ConceptSlotStatus.FAILED,
                ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS, false, false))).canResume()).isFalse();
        assertThat(policy.evaluate(ConceptFactoryRunStatus.FAILED, true, List.of(
            new SlotState(ConceptSlotStatus.FAILED,
                ConceptAttemptError.PERMANENT_PROVIDER_FAILURE, false, false))).canResume()).isFalse();
        assertThat(policy.evaluate(ConceptFactoryRunStatus.FAILED, false, List.of(
            new SlotState(ConceptSlotStatus.FAILED,
                ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true, false))).nextAction())
            .isEqualTo("START_NEW_RUN");
    }

    @Test
    void requestContractFailureCannotResumeTheSameBrokenRun() {
        var result = policy.evaluate(ConceptFactoryRunStatus.FAILED, true, List.of(
            new SlotState(ConceptSlotStatus.FAILED,
                ConceptAttemptError.REQUEST_CONTRACT_INVALID, false, false),
            new SlotState(ConceptSlotStatus.QUEUED, null, false, false)));

        assertThat(result.canResume()).isFalse();
        assertThat(result.nextAction()).isEqualTo("FIX_SYSTEM_AND_START_NEW_RUN");
        assertThat(result.reason()).isEqualTo("REQUEST_CONTRACT_INVALID");
    }
}
