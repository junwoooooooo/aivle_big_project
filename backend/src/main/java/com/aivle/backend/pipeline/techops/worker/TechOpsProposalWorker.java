package com.aivle.backend.pipeline.techops.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.techops.application.TechOpsProposalCompletionService;
import com.aivle.backend.pipeline.techops.application.TechOpsProposalCompletionService.Outcome;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
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
public class TechOpsProposalWorker {
    private static final TaskType TYPE = TaskType.TECH_OPS_PROPOSAL;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final TechOpsProposalCompletionService completion;
    private final JobEventPublisher events;
    private final String workerId = "tech-ops-proposal-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.tech-ops-proposal-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.tech-ops-proposal-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            publish(taskRuns.workerContext(id), "QUEUED", "job.tech-ops.proposal.queued",
                JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = taskRuns.claimNext(TYPE, workerId, Duration.ofMinutes(5), Duration.ofMinutes(3));
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            if (!completion.start(claim, context)) {
                publish(context, "FAILED", "job.tech-ops.proposal.stale", JobEvent.Status.FAILED,
                    "STALE_ACTION_RESULT");
                return true;
            }
            publish(context, "GENERATING", "job.tech-ops.proposal.generating", JobEvent.Status.RUNNING, null);
            var response = ai.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(3));
            Outcome outcome = completion.complete(claim, context, response);
            if (outcome == Outcome.STALE) {
                publish(context, "FAILED", "job.tech-ops.proposal.stale", JobEvent.Status.FAILED,
                    "STALE_ACTION_RESULT");
            } else {
                publish(context, "COMPLETED", "job.tech-ops.proposal.completed", JobEvent.Status.COMPLETED, null);
            }
        } catch (ExecutionFailure failure) {
            terminalFailure(claim, context, failure.code(), failure.reason(), failure.retryable());
        } catch (RuntimeException failure) {
            log.warn("TechOps proposal worker failed taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName());
            terminalFailure(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
        }
        return true;
    }

    private void terminalFailure(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        try { completion.fail(claim, context, code, reason, retryable); }
        finally { publish(context, "FAILED", "job.tech-ops.proposal.failed",
            JobEvent.Status.FAILED, safeCode(code, reason)); }
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
