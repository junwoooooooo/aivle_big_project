package com.aivle.backend.taskrun.e2e;

import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Provider-free infrastructure seam for the disposable Docker E2E profile.
 *
 * <p>The seam deliberately creates ordinary {@link TaskRun} / {@link TaskResult}
 * records. It is not a product endpoint and is absent unless the {@code e2e}
 * Spring profile is active.</p>
 */
@Service
@Profile("e2e")
public class E2eTaskRunService {
    private static final TaskType TYPE = TaskType.CONCEPT_CANDIDATE;
    private static final String SUBJECT_TYPE = "E2E_PIPELINE";
    private static final String SUBJECT_ID = "CURRENT_TASK_RUN";
    private static final String RESULT_SCHEMA = "e2e-task-result-v1";
    private static final Duration NORMAL_LEASE = Duration.ofSeconds(30);
    private static final Duration NORMAL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration EXPIRING_BOUNDARY = Duration.ofMillis(500);

    private final TaskRunService taskRuns;
    private final TaskResultRepository taskResults;
    private final ProjectEvidenceArtifactService artifacts;
    private final CanonicalInputHasher hasher;
    private final ObjectMapper mapper;
    private final TaskScheduler scheduler;
    private final RestClient aiServer;

    public E2eTaskRunService(TaskRunService taskRuns, TaskResultRepository taskResults,
            ProjectEvidenceArtifactService artifacts, CanonicalInputHasher hasher,
            ObjectMapper mapper, TaskScheduler scheduler,
            @Qualifier("aiServerRestClient") RestClient aiServer) {
        this.taskRuns = taskRuns;
        this.taskResults = taskResults;
        this.artifacts = artifacts;
        this.hasher = hasher;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.aiServer = aiServer;
    }

    public StartResult start(Long ownerId, Long projectId, Scenario scenario,
            String idempotencyKey, String correlationId) {
        String input = mapper.writeValueAsString(Map.of(
            "schemaVersion", "e2e-task-input-v1",
            "scenario", scenario.name()
        ));
        String inputHash = hasher.hash(TYPE, "1.0", "ko-KR", input);
        int maxAttempts = scenario == Scenario.AI_DEPENDENCY || scenario == Scenario.ARTIFACT ? 2 : 1;
        var created = taskRuns.createWithDisposition(ownerId, projectId, TYPE,
            SUBJECT_TYPE, SUBJECT_ID, input, inputHash, idempotencyKey,
            correlationId, maxAttempts);
        if (created.createdNew()) {
            scheduler.schedule(() -> execute(created.taskRun().getId()),
                Instant.now().plusMillis(100));
        }
        return new StartResult(created.taskRun().getId(), created.createdNew(), created.replayed());
    }

    public JsonNode result(Long ownerId, Long projectId, String taskRunId) {
        TaskRun run = taskRuns.getOwned(ownerId, projectId, taskRunId);
        if (run.getFinalResultId() == null) {
            throw new TaskRunFailure("RESOURCE_NOT_FOUND", "TASK_RESULT_NOT_FOUND",
                HttpStatus.NOT_FOUND, false);
        }
        TaskResult result = taskResults.findById(run.getFinalResultId())
            .filter(value -> value.getValidationState() == TaskResultValidationState.ADOPTED)
            .orElseThrow(() -> new TaskRunFailure("RESOURCE_NOT_FOUND", "TASK_RESULT_NOT_FOUND",
                HttpStatus.NOT_FOUND, false));
        return mapper.readTree(result.getResultJson());
    }

    void execute(String taskRunId) {
        TaskRunWorkerContext context = taskRuns.workerContext(taskRunId);
        Scenario scenario = Scenario.valueOf(mapper.readTree(context.inputSnapshot())
            .path("scenario").asText());
        Duration boundary = scenario == Scenario.TIMEOUT || scenario == Scenario.STALE
            ? EXPIRING_BOUNDARY : NORMAL_TIMEOUT;
        var claim = taskRuns.claim(taskRunId, "docker-e2e", boundary, boundary);
        taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        try {
            switch (scenario) {
                case NORMAL -> adopt(context, claim, Map.of(
                    "status", "processed", "taskRunId", taskRunId));
                case ARTIFACT -> executeArtifact(context, claim);
                case AI_DEPENDENCY -> executeAiDependency(context, claim);
                case MALFORMED -> taskRuns.rejectAndFail(taskRunId, claim.taskAttemptId(),
                    claim.claimToken(), "{\"unexpected\":true}", RESULT_SCHEMA,
                    "RESULT_FIELD_CONSTRAINT_VIOLATION");
                case CHECKSUM -> executeChecksumFailure(context, claim);
                case TIMEOUT -> executeExpiry(context, claim, false);
                case STALE -> executeExpiry(context, claim, true);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failIfActive(context, claim, "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
        } catch (RuntimeException failure) {
            failIfActive(context, claim, "DEPENDENCY_UNAVAILABLE",
                scenario == Scenario.ARTIFACT ? "ARTIFACT_STORAGE_FAILED" : "MODEL_DEPENDENCY_UNAVAILABLE",
                scenario == Scenario.ARTIFACT || scenario == Scenario.AI_DEPENDENCY);
        }
    }

    private void executeArtifact(TaskRunWorkerContext context, TaskRunService.Claim claim) {
        byte[] content = "current-task-run-artifact\n".getBytes(StandardCharsets.UTF_8);
        var artifact = artifacts.storeGenerated(context.ownerId(), context.projectId(),
            "current-task-run-result.txt", "text/plain", content);
        adopt(context, claim, Map.of(
            "status", "processed",
            "taskRunId", context.taskRunId(),
            "artifactId", artifact.artifactId(),
            "artifactBytes", artifact.sizeBytes()
        ));
    }

    private void executeAiDependency(TaskRunWorkerContext context, TaskRunService.Claim claim) {
        aiServer.get().uri("/health/ready").retrieve().toBodilessEntity();
        adopt(context, claim, Map.of(
            "status", "dependency-ready", "taskRunId", context.taskRunId()));
    }

    private void executeChecksumFailure(TaskRunWorkerContext context, TaskRunService.Claim claim) {
        try {
            taskRuns.adopt(context.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "{\"status\":\"must-not-be-adopted\"}", "sha256:" + "0".repeat(64),
                RESULT_SCHEMA);
        } catch (TaskRunFailure expectedHashRejection) {
            taskRuns.fail(context.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "INVALID_REQUEST", "RESULT_SCHEMA_INVALID", false);
        }
    }

    private void executeExpiry(TaskRunWorkerContext context, TaskRunService.Claim claim,
            boolean submitLateResult) throws InterruptedException {
        Thread.sleep(EXPIRING_BOUNDARY.plusMillis(250).toMillis());
        List<String> recovered = taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE));
        if (!recovered.contains(context.taskRunId())) {
            throw new IllegalStateException("E2E expiry fault was not reached");
        }
        if (!submitLateResult) return;
        try {
            taskRuns.adopt(context.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "{\"status\":\"late-result-must-not-be-current\"}", context.inputHash(),
                RESULT_SCHEMA);
        } catch (TaskRunFailure expectedLateRejection) {
            // TaskRunService persists the REJECTED result and preserves the terminal timeout.
        }
    }

    private void adopt(TaskRunWorkerContext context, TaskRunService.Claim claim, Object payload) {
        taskRuns.adopt(context.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(payload), context.inputHash(), RESULT_SCHEMA);
    }

    private void failIfActive(TaskRunWorkerContext context, TaskRunService.Claim claim,
            String code, String reason, boolean retryable) {
        try {
            taskRuns.fail(context.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                code, reason, retryable);
        } catch (RuntimeException ignoredTerminalRace) {
            // A timeout/recovery may already have made the run terminal.
        }
    }

    public enum Scenario {
        NORMAL,
        ARTIFACT,
        AI_DEPENDENCY,
        MALFORMED,
        CHECKSUM,
        TIMEOUT,
        STALE
    }

    public record StartResult(String taskRunId, boolean createdNew, boolean replayed) {}
}
