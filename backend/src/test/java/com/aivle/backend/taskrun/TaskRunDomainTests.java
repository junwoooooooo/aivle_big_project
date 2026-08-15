package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.taskrun.domain.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskRunDomainTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void terminalFailureCannotBeClaimedAgain() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt first = TaskAttempt.claim(run, "worker-1", now, now.plusSeconds(30), now.plusMinutes(2));
        first.start(first.getClaimToken(), now);
        first.fail(first.getClaimToken(), "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true, now.plusSeconds(1));
        run.fail("AI_SERVICE_UNAVAILABLE", true, now.plusSeconds(1));
        assertThat(run.terminal()).isTrue();
        assertThatThrownBy(() -> TaskAttempt.claim(run, "worker-2", now.plusSeconds(2),
            now.plusSeconds(32), now.plusMinutes(2))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staleClaimCannotCompleteAttempt() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt attempt = TaskAttempt.claim(run, "worker", now, now.plusSeconds(30), now.plusMinutes(2));
        attempt.start(attempt.getClaimToken(), now);
        assertThatThrownBy(() -> attempt.succeed("wrong-token", now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelIsIdempotentForTerminalRun() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now(); run.cancel(now); run.cancel(now.plusSeconds(1));
        assertThat(run.getState()).isEqualTo(TaskRunState.CANCELLED);
        assertThat(run.isRetryable()).isFalse();
    }

    @Test
    void resultRequiresValidationBeforeAdoption() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt attempt = TaskAttempt.claim(run, "worker", now, now.plusSeconds(30), now.plusMinutes(2));
        attempt.start(attempt.getClaimToken(), now);
        TaskResult result = TaskResult.received(run, attempt, "{}", HASH, "1.0", now);
        assertThatThrownBy(() -> result.adopt(now)).isInstanceOf(IllegalStateException.class);
        result.validateResult(now); result.adopt(now);
        assertThat(result.getValidationState()).isEqualTo(TaskResultValidationState.ADOPTED);
    }

    @Test
    void terminalRunRejectsMutation() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF_DERIVATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now(); run.cancel(now);
        assertThatThrownBy(() -> run.fail("ERROR", true, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.succeed("result", now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void needsInputTerminatesRunAndAttemptWithoutMakingThemClaimable() {
        TaskRun run = TaskRun.create(null, TaskType.CONCEPT_FACTORY_RUN, "CONCEPT_FACTORY_RUN", "run-1", "{}", HASH, "key", "correlation", 1);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt attempt = TaskAttempt.claim(run, "worker", now, now.plusSeconds(30), now.plusMinutes(2));
        attempt.start(attempt.getClaimToken(), now);
        attempt.needsInput(attempt.getClaimToken(), now.plusSeconds(1));
        run.needsInput(now.plusSeconds(1));

        assertThat(run.getState()).isEqualTo(TaskRunState.NEEDS_INPUT);
        assertThat(attempt.getState()).isEqualTo(TaskAttemptState.NEEDS_INPUT);
        assertThat(run.terminal()).isTrue();
        assertThat(run.isRetryable()).isFalse();
    }

    @Test
    void claimedAttemptCanFailBeforeExecutionStarts() {
        TaskRun run = TaskRun.create(null, TaskType.CONCEPT_FACTORY_RUN, "CONCEPT_FACTORY_RUN", "run-1", "{}", HASH, "key", "correlation", 1);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt attempt = TaskAttempt.claim(run, "worker", now, now.plusSeconds(30), now.plusMinutes(2));

        attempt.fail(attempt.getClaimToken(), "EXECUTION_FAILED", "START_BOUNDARY_FAILURE", false, now.plusSeconds(1));
        run.fail("AI_SERVICE_UNAVAILABLE", false, now.plusSeconds(1));

        assertThat(attempt.getState()).isEqualTo(TaskAttemptState.FAILED);
        assertThat(run.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(run.terminal()).isTrue();
    }
}
