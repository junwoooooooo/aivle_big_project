package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.service.*;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class TaskRunServiceIntegrationTests {
    @Autowired TaskRunService service;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired MockMvc mockMvc;
    @Autowired CanonicalInputHasher hasher;
    @Autowired TaskResultRepository results;

    private String hash() {
        return hash("{}");
    }

    private String hash(String input) {
        return hasher.hash(TaskType.IDEA_INTERPRETATION, "1.0", "ko-KR", input);
    }

    @Test
    void createsReplaysClaimsAndAtomicallyAdoptsResult() {
        User owner = users.saveAndFlush(User.create("task-owner@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "task project", null, null));
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "subject-1", "{}", hash(), "create-key", "correlation-1", 3);
        assertThat(service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "subject-1", "{}", hash(), "create-key", "correlation-1", 3).getId()).isEqualTo(run.getId());
        String changedInput = "{\"changed\":true}";
        assertThatThrownBy(() -> service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "subject-1", changedInput, hash(changedInput), "create-key", "correlation-1", 3))
            .isInstanceOfSatisfying(TaskRunFailure.class, failure -> assertThat(failure.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThatThrownBy(() -> service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "subject-1", "{}", hash(), "other-key", "correlation-1", 3))
            .isInstanceOfSatisfying(TaskRunFailure.class, failure -> assertThat(failure.getCode()).isEqualTo("TASK_ALREADY_RUNNING"));
        TaskRunService.Claim claim = service.claim(run.getId(), "worker-1", Duration.ofSeconds(30), Duration.ofMinutes(2));
        service.startExecution(run.getId(), claim.taskAttemptId(), claim.claimToken());
        service.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(), "{\"readiness\":\"APPROPRIATE\"}", hash(), "1.0");
        TaskRun completed = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(completed.getState()).isEqualTo(TaskRunState.SUCCEEDED);
        assertThat(completed.getFinalResultId()).isNotBlank();
    }

    @Test
    void crossOwnerIs404AndCancelRejectsLateResult() {
        User owner = users.saveAndFlush(User.create("task-owner2@example.com", "hash", "owner"));
        User other = users.saveAndFlush(User.create("task-other@example.com", "hash", "other"));
        Project project = projects.saveAndFlush(Project.create(owner, "task project 2", null, null));
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "subject-2", "{}", hash(), "create-key-2", "correlation-2", 3);
        assertThatThrownBy(() -> service.getOwned(other.getId(), project.getId(), run.getId()))
            .isInstanceOfSatisfying(TaskRunFailure.class, failure -> assertThat(failure.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        TaskRunService.Claim claim = service.claim(run.getId(), "worker-1", Duration.ofSeconds(30), Duration.ofMinutes(2));
        service.startExecution(run.getId(), claim.taskAttemptId(), claim.claimToken());
        service.cancel(owner.getId(), project.getId(), run.getId());
        assertThatThrownBy(() -> service.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(), "{}", hash(), "1.0"))
            .isInstanceOfSatisfying(TaskRunFailure.class, failure -> assertThat(failure.getCode()).isEqualTo("AI_RESULT_INVALID"));
        assertThat(results.findByTaskRunId(run.getId())).singleElement()
            .extracting(TaskResult::getValidationState).isEqualTo(TaskResultValidationState.REJECTED);
    }

    @Test
    void publicV2RetryReplayCancelAndTerminalGetAreOwnerScoped() throws Exception {
        User owner = users.saveAndFlush(User.create("task-api-owner@example.com", "hash", "owner"));
        User other = users.saveAndFlush(User.create("task-api-other@example.com", "hash", "other"));
        Project project = projects.saveAndFlush(Project.create(owner, "task api project", null, null));
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.QUICK_ASSESSMENT,
            "TASK_API_TEST", "subject-api", "{}",
            hasher.hash(TaskType.QUICK_ASSESSMENT, "1.0", "ko-KR", "{}"), "create-api", "correlation-api", 3);
        TaskRunService.Claim claim = service.claim(run.getId(), "worker-api", Duration.ofSeconds(30), Duration.ofMinutes(2));
        service.startExecution(run.getId(), claim.taskAttemptId(), claim.claimToken());
        service.fail(run.getId(), claim.taskAttemptId(), claim.claimToken(), "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);

        mockMvc.perform(post("/api/v2/projects/{projectId}/task-runs/{taskRunId}/retry", project.getId(), run.getId())
                .header("X-User-Id", owner.getId()).header("Idempotency-Key", "retry-api"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.id").value(run.getId()))
            .andExpect(jsonPath("$.data.state").value("QUEUED"));
        mockMvc.perform(post("/api/v2/projects/{projectId}/task-runs/{taskRunId}/retry", project.getId(), run.getId())
                .header("X-User-Id", owner.getId()).header("Idempotency-Key", "retry-api"))
            .andExpect(status().isAccepted());
        assertThat(service.getOwned(owner.getId(), project.getId(), run.getId()).getAttemptCount()).isEqualTo(2);
        mockMvc.perform(post("/api/v2/projects/{projectId}/task-runs/{taskRunId}/cancel", project.getId(), run.getId())
                .header("X-User-Id", owner.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("CANCELLED"));
        mockMvc.perform(get("/api/v2/projects/{projectId}/task-runs/{taskRunId}", project.getId(), run.getId())
                .header("X-User-Id", owner.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.workerId").doesNotExist())
            .andExpect(jsonPath("$.data.taskAttemptId").doesNotExist());
        mockMvc.perform(get("/api/v2/projects/{projectId}/task-runs/{taskRunId}", project.getId(), run.getId())
                .header("X-User-Id", other.getId()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiredLeaseAutomaticallyQueuesAnotherAttemptUntilLimitIsExhausted() {
        User owner = users.saveAndFlush(User.create("task-recovery@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "task recovery", null, null));
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.QUICK_ASSESSMENT,
            "TASK_RECOVERY_TEST", "subject-recovery", "{}",
            hasher.hash(TaskType.QUICK_ASSESSMENT, "1.0", "ko-KR", "{}"), "recovery-key", "correlation-recovery", 2);

        TaskRunService.Claim first = service.claim(run.getId(), "worker-1", Duration.ZERO, Duration.ofMinutes(2));
        assertThat(service.recoverExpired(Duration.ZERO, java.util.List.of(TaskType.QUICK_ASSESSMENT))).isEqualTo(1);
        assertThat(service.getOwned(owner.getId(), project.getId(), run.getId()).getState()).isEqualTo(TaskRunState.QUEUED);

        TaskRunService.Claim second = service.claim(run.getId(), "worker-2", Duration.ZERO, Duration.ofMinutes(2));
        assertThat(second.taskAttemptId()).isNotEqualTo(first.taskAttemptId());
        assertThat(service.recoverExpired(Duration.ZERO, java.util.List.of(TaskType.QUICK_ASSESSMENT))).isEqualTo(1);
        TaskRun exhausted = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(exhausted.getState()).isEqualTo(TaskRunState.TIMED_OUT);
        assertThat(exhausted.isRetryable()).isFalse();
        assertThat(exhausted.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void retryableLegalFailureAutomaticallyQueuesNextAttemptUntilLimit() {
        User owner = users.saveAndFlush(User.create("legal-auto-retry@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "legal auto retry", null, null));
        String input = "{}";
        String inputHash = hasher.hash(TaskType.IDEA_LEGAL_PRECHECK, "1.0", "ko-KR", input);
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.IDEA_LEGAL_PRECHECK,
            "IDEA_ORIGIN_VERSION", "1", input, inputHash, "legal-auto-retry", "legal-correlation", 2);

        TaskRunService.Claim first = service.claim(run.getId(),
            "legal-worker-1", Duration.ofSeconds(30), Duration.ofMinutes(2));
        service.startExecution(run.getId(), first.taskAttemptId(), first.claimToken());
        service.failWithLegalAutoRetry(run.getId(), first.taskAttemptId(), first.claimToken(),
            "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);
        assertThat(service.getOwned(owner.getId(), project.getId(), run.getId()).getState())
            .isEqualTo(TaskRunState.QUEUED);

        TaskRunService.Claim second = service.claim(run.getId(),
            "legal-worker-2", Duration.ofSeconds(30), Duration.ofMinutes(2));
        service.startExecution(run.getId(), second.taskAttemptId(), second.claimToken());
        service.failWithLegalAutoRetry(run.getId(), second.taskAttemptId(), second.claimToken(),
            "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);
        TaskRun exhausted = service.getOwned(owner.getId(), project.getId(), run.getId());
        assertThat(exhausted.getState()).isEqualTo(TaskRunState.FAILED);
        assertThat(exhausted.isRetryable()).isFalse();
        assertThat(exhausted.getAttemptCount()).isEqualTo(2);
    }
}
