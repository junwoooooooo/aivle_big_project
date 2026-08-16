package com.aivle.backend.pipeline.launchreadiness.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessService;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessService.StaleInputException;
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
public class LaunchReadinessWorker {
    private static final List<TaskType> TYPES = List.of(TaskType.LAUNCH_TECHNOLOGY_READINESS, TaskType.LAUNCH_OPERATIONS_READINESS);
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final LaunchReadinessService readiness;
    private final String workerId = "launch-readiness-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.launch-readiness-poll-interval-ms:1000}")
    public void poll() { for (TaskType type : TYPES) if (processOne(type)) break; }
    @Scheduled(fixedDelayString = "${app.task-run.launch-readiness-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, TYPES)) {
            var context = taskRuns.workerContext(id);
            readiness.publish(context.projectId(), id, "QUEUED", "job.launch-readiness.queued", JobEvent.Status.QUEUED, null);
        }
    }
    boolean processOne(TaskType type) {
        var claim = taskRuns.claimNext(type, workerId, Duration.ofMinutes(8), Duration.ofMinutes(7));
        if (claim == null) return false;
        var context = taskRuns.workerContext(claim.taskRunId());
        taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (!readiness.currentInput(context)) {
            readiness.fail(claim, "EXECUTION_FAILED", "STALE_ACTION_RESULT", false);
            readiness.publish(context.projectId(), context.taskRunId(), "FAILED", "job.launch-readiness.stale", JobEvent.Status.FAILED, "STALE_ACTION_RESULT");
            return true;
        }
        try {
            readiness.publish(context.projectId(), context.taskRunId(), "ANALYZING", "job.launch-readiness.analyzing", JobEvent.Status.RUNNING, null);
            var response = ai.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(7));
            readiness.complete(claim, context, response);
            readiness.publish(context.projectId(), context.taskRunId(), "COMPLETED", "job.launch-readiness.completed", JobEvent.Status.COMPLETED, null);
        } catch (StaleInputException stale) {
            readiness.fail(claim, "EXECUTION_FAILED", "STALE_ACTION_RESULT", false);
            readiness.publish(context.projectId(), context.taskRunId(), "FAILED", "job.launch-readiness.stale", JobEvent.Status.FAILED, "STALE_ACTION_RESULT");
        } catch (ExecutionFailure failure) {
            readiness.fail(claim, failure.code(), failure.reason(), failure.retryable());
            readiness.publish(context.projectId(), context.taskRunId(), "FAILED", "job.launch-readiness.failed", JobEvent.Status.FAILED, safeCode(failure));
        } catch (RuntimeException failure) {
            log.warn("Launch readiness worker rejected result taskRunId={} type={}", claim.taskRunId(), failure.getClass().getSimpleName());
            readiness.reject(claim, "AI_RESULT_INVALID");
            readiness.publish(context.projectId(), context.taskRunId(), "FAILED", "job.launch-readiness.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
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
