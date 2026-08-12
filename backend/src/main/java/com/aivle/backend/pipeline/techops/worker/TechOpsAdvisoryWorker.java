package com.aivle.backend.pipeline.techops.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.techops.application.TechOpsAdvisoryService;
import com.aivle.backend.pipeline.techops.application.TechOpsAdvisoryService.StaleSourceException;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TechOpsAdvisoryWorker {
    private static final TaskType TYPE = TaskType.TECH_OPS_ADVISORY;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final TechOpsAdvisoryService advisory;
    private final String workerId = "tech-ops-advisory-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.tech-ops-advisory-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.tech-ops-advisory-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            var context = taskRuns.workerContext(id);
            advisory.publish(context.projectId(), id, "QUEUED", "job.tech-ops.advisory.queued",
                JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        var claim = taskRuns.claimNext(TYPE, workerId, Duration.ofMinutes(7), Duration.ofMinutes(6));
        if (claim == null) return false;
        var context = taskRuns.workerContext(claim.taskRunId());
        taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (!advisory.validateSources(context)) {
            advisory.fail(claim, "EXECUTION_FAILED", "STALE_ACTION_RESULT", false);
            advisory.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.tech-ops.advisory.stale", JobEvent.Status.FAILED, "STALE_ACTION_RESULT");
            return true;
        }
        try {
            advisory.publish(context.projectId(), context.taskRunId(), "GENERATING_ADVISORY",
                "job.tech-ops.advisory.generating", JobEvent.Status.RUNNING, null);
            var response = ai.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(6));
            advisory.complete(claim, context, response);
            advisory.publish(context.projectId(), context.taskRunId(), "COMPLETED",
                "job.tech-ops.advisory.completed", JobEvent.Status.COMPLETED, null);
        } catch (StaleSourceException stale) {
            advisory.fail(claim, "EXECUTION_FAILED", "STALE_ACTION_RESULT", false);
            advisory.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.tech-ops.advisory.stale", JobEvent.Status.FAILED, "STALE_ACTION_RESULT");
        } catch (ExecutionFailure failure) {
            advisory.fail(claim, failure.code(), failure.reason(), failure.retryable());
            advisory.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.tech-ops.advisory.failed", JobEvent.Status.FAILED, safeCode(failure));
        } catch (RuntimeException failure) {
            log.warn("TechOps advisory worker rejected result taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName());
            advisory.reject(claim, null, "AI_RESULT_INVALID");
            advisory.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.tech-ops.advisory.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
        }
        return true;
    }

    private String safeCode(ExecutionFailure failure) {
        if ("DEADLINE_EXCEEDED".equals(failure.code())) return "TASK_TIMEOUT";
        if ("RATE_LIMITED".equals(failure.code())) return "RATE_LIMITED";
        if ("RESULT_SCHEMA_INVALID".equals(failure.code())) return "AI_RESULT_INVALID";
        return "AI_SERVICE_UNAVAILABLE";
    }
}
