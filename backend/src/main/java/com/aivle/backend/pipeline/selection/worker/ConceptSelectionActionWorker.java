package com.aivle.backend.pipeline.selection.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionActionCompletionService;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionActionCompletionService.Outcome;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConceptSelectionActionWorker {
    private static final List<TaskType> TYPES = List.of(
        TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE, TaskType.CONCEPT_DELTA_LEGAL_REVIEW);
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final ConceptSelectionActionCompletionService completion;
    private final JobEventPublisher events;
    private final String workerId = "concept-selection-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.concept-selection-poll-interval-ms:1000}")
    public void poll() {
        processOne();
    }

    @Scheduled(fixedDelayString = "${app.task-run.concept-selection-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, TYPES)) {
            publish(taskRuns.workerContext(id), "QUEUED", "job.concept-selection.queued",
                JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = claim();
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            if (!completion.start(claim, context)) {
                publish(context, "FAILED", "job.concept-selection.stale", JobEvent.Status.FAILED,
                    "STALE_ACTION_RESULT");
                return true;
            }
            String progressKey = context.taskType() == TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE
                ? "job.concept-selection.alternative.running" : "job.concept-selection.delta-legal.running";
            publish(context, "RUNNING", progressKey, JobEvent.Status.RUNNING, null);
            ExecutionResponse response = ai.executeWorker(
                context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(3));
            Outcome outcome = completion.complete(claim, context, response);
            if (outcome == Outcome.STALE) {
                publish(context, "FAILED", "job.concept-selection.stale", JobEvent.Status.FAILED,
                    "STALE_ACTION_RESULT");
            } else {
                String key = outcome == Outcome.LEGAL_INELIGIBLE
                    ? "job.concept-selection.delta-legal.ineligible" : "job.concept-selection.completed";
                publish(context, "COMPLETED", key, JobEvent.Status.COMPLETED, null);
            }
        } catch (ExecutionFailure failure) {
            terminalFailure(claim, context, failure.code(), failure.reason(), failure.retryable());
        } catch (RuntimeException failure) {
            log.warn("Concept selection action failed taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName());
            terminalFailure(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
        }
        return true;
    }

    private TaskRunService.Claim claim() {
        for (TaskType type : TYPES) {
            TaskRunService.Claim claim = taskRuns.claimNext(
                type, workerId, Duration.ofMinutes(5), Duration.ofMinutes(3));
            if (claim != null) return claim;
        }
        return null;
    }

    private void terminalFailure(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        try {
            completion.fail(claim, context, code, reason, retryable);
        } finally {
            publish(context, "FAILED", "job.concept-selection.failed", JobEvent.Status.FAILED,
                safeCode(code, reason));
        }
    }

    private String safeCode(String code, String reason) {
        if ("STALE_ACTION_RESULT".equals(reason)) return reason;
        if ("DEADLINE_EXCEEDED".equals(code)) return "TASK_TIMEOUT";
        if ("RATE_LIMITED".equals(code)) return "RATE_LIMITED";
        return "AI_SERVICE_UNAVAILABLE";
    }

    private void publish(TaskRunWorkerContext context, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), stage, key, status, key, Map.of(), code));
    }
}
