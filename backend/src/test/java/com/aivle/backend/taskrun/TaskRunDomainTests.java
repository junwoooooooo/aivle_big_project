package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.taskrun.domain.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskRunDomainTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void retryCreatesNewAttemptNumberOnNextClaim() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_INTERPRETATION, "IDEA_INTERPRETATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt first = TaskAttempt.claim(run, "worker-1", now, now.plusSeconds(30), now.plusMinutes(2));
        first.start(first.getClaimToken(), now);
        first.fail(first.getClaimToken(), "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true, now.plusSeconds(1));
        run.fail("AI_SERVICE_UNAVAILABLE", true, now.plusSeconds(1));
        run.queueRetry();
        TaskAttempt second = TaskAttempt.pending(run, now.plusMinutes(2));
        second.claim("worker-2", now.plusSeconds(2), now.plusSeconds(32));
        assertThat(first.getAttemptNumber()).isEqualTo(1);
        assertThat(second.getAttemptNumber()).isEqualTo(2);
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void staleClaimCannotCompleteAttempt() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_INTERPRETATION, "IDEA_INTERPRETATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now();
        TaskAttempt attempt = TaskAttempt.claim(run, "worker", now, now.plusSeconds(30), now.plusMinutes(2));
        attempt.start(attempt.getClaimToken(), now);
        assertThatThrownBy(() -> attempt.succeed("wrong-token", now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelIsIdempotentForTerminalRun() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_INTERPRETATION, "IDEA_INTERPRETATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now(); run.cancel(now); run.cancel(now.plusSeconds(1));
        assertThat(run.getState()).isEqualTo(TaskRunState.CANCELLED);
        assertThat(run.isRetryable()).isFalse();
    }

    @Test
    void resultRequiresValidationBeforeAdoption() {
        TaskRun run = TaskRun.create(null, TaskType.IDEA_INTERPRETATION, "IDEA_INTERPRETATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
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
        TaskRun run = TaskRun.create(null, TaskType.IDEA_INTERPRETATION, "IDEA_INTERPRETATION_RUN", "subject-1", "{}", HASH, "key", "correlation", 3);
        LocalDateTime now = LocalDateTime.now(); run.cancel(now);
        assertThatThrownBy(() -> run.fail("ERROR", true, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.succeed("result", now)).isInstanceOf(IllegalStateException.class);
    }
}
