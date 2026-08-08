package com.aivle.backend.pipeline.idea.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.idea.application.IdeaBriefDerivationCommitService;
import com.aivle.backend.pipeline.idea.application.IdeaBriefDerivationCommitService.CommitResult;
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
public class IdeaBriefDerivationWorker {
    private static final TaskType TYPE = TaskType.IDEA_BRIEF_DERIVATION;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient aiClient;
    private final IdeaBriefDerivationCommitService completion;
    private final JobEventPublisher events;
    private final String workerId = "idea-brief-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.idea-brief-poll-interval-ms:1000}")
    public void poll() {
        processOne();
    }

    @Scheduled(fixedDelayString = "${app.task-run.idea-brief-recovery-interval-ms:5000}")
    public void recover() {
        for (String taskRunId : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            TaskRunWorkerContext context = taskRuns.workerContext(taskRunId);
            publish(context, "QUEUED", "job.idea.queued", JobEvent.Status.QUEUED, Map.of(), null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = taskRuns.claimNext(TYPE, workerId, Duration.ofMinutes(5), Duration.ofMinutes(3));
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        CommitResult committed;
        try {
            publish(context, "CLAIMED", "job.idea.started", JobEvent.Status.RUNNING, Map.of(), null);
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            publish(context, "SAFETY_REVIEW", "job.idea.extracting", JobEvent.Status.RUNNING, Map.of(), null);
            ExecutionResponse response = aiClient.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(3));
            publish(context, "IDEA_INTERPRETATION", "job.idea.questions.preparing", JobEvent.Status.RUNNING, Map.of(), null);
            publish(context, "INTERPRETATION_COMMIT", "job.idea.brief.preparing", JobEvent.Status.RUNNING, Map.of(), null);
            committed = completion.complete(claim, context, response);
        } catch (ExecutionFailure failure) {
            terminalFailure(claim, context, failure.code(), failure.reason(), failure.retryable());
            return true;
        } catch (RuntimeException failure) {
            log.warn("Idea Brief worker failed taskRunId={} type={}", claim.taskRunId(), failure.getClass().getSimpleName());
            terminalFailure(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
            return true;
        }
        JobEvent.Status status = "NEEDS_INPUT".equals(committed.status())
            ? JobEvent.Status.NEEDS_INPUT : JobEvent.Status.COMPLETED;
        publish(context, status == JobEvent.Status.NEEDS_INPUT ? "NEEDS_INPUT" : "SUCCEEDED",
            "job.idea.completed", status,
            Map.of("questionCount", committed.questionCount()), null);
        return true;
    }

    private void terminalFailure(
        TaskRunService.Claim claim,
        TaskRunWorkerContext context,
        String code,
        String reason,
        boolean retryable
    ) {
        try {
            taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        } finally {
            completion.fail(context.subjectId(), context.projectId());
            publish(context, "FAILED", "job.idea.failed", JobEvent.Status.FAILED, Map.of(), code);
        }
    }

    private void publish(
        TaskRunWorkerContext context,
        String stage,
        String key,
        JobEvent.Status status,
        Map<String, ?> params,
        String code
    ) {
        events.publish(new JobEventPublisher.Command(
            context.projectId(), context.taskRunId(), context.taskRunId(), stage, key,
            status, key, params, code
        ));
    }
}
