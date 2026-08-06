package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConceptEligibilityBatchRestartTests {

    private ConceptEligibilityBatch batch() {
        return ConceptEligibilityBatch.create(
                null, null, null, "sha256:test", "concept-eligibility-v1", "1.0", 3, 2, 9);
    }

    @Test
    void aiResultInvalidFailedBatchAllowsExplicitNewBatchWithoutInspectingCandidates() {
        ConceptEligibilityBatch failed = batch();
        failed.fail("AI_RESULT_INVALID", false);

        assertThat(failed.allowsManualRestart()).isTrue();
        assertThat(failed.getInspectedCandidates()).isZero();
        assertThat(failed.getEligibleCandidates()).isZero();
    }

    @Test
    void runningAndCompletedBatchesAreReused() {
        ConceptEligibilityBatch running = batch();
        ConceptEligibilityBatch completed = batch();
        completed.complete();

        assertThat(running.allowsManualRestart()).isFalse();
        assertThat(completed.allowsManualRestart()).isFalse();
    }

    @Test
    void configurationAndRetryableFailuresKeepExistingRestartPolicy() {
        ConceptEligibilityBatch configuration = batch();
        configuration.fail("AI_CONFIGURATION_INVALID", false);
        ConceptEligibilityBatch retryable = batch();
        retryable.fail("AI_SERVICE_UNAVAILABLE", true);
        ConceptEligibilityBatch permanent = batch();
        permanent.fail("PROJECT_STAGE_INVALID", false);

        assertThat(configuration.allowsManualRestart()).isTrue();
        assertThat(retryable.allowsManualRestart()).isTrue();
        assertThat(permanent.allowsManualRestart()).isFalse();
    }

    @Test
    void manualRestartUsesANewBatchAggregate() {
        ConceptEligibilityBatch failed = batch();
        failed.fail("AI_RESULT_INVALID", false);
        ConceptEligibilityBatch replacement = batch();

        assertThat(failed).isNotSameAs(replacement);
        assertThat(replacement.getState()).isEqualTo(ConceptEligibilityBatch.State.GENERATING);
    }
}
