package com.aivle.backend.pipeline.marketing.visual.worker;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.marketing.visual.application.MarketingVisualCompletionService;
import com.aivle.backend.pipeline.marketing.visual.application.MarketingVisualService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
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
public class MarketingVisualWorker {
    private static final TaskType TYPE = TaskType.MARKETING_VISUAL_GENERATION;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final MarketingVisualService visuals;
    private final MarketingVisualCompletionService completion;
    private final String workerId = "marketing-visual-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.marketing-visual-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.marketing-visual-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            TaskRunWorkerContext context = taskRuns.workerContext(id);
            visuals.publish(context.projectId(), id, "QUEUED", "job.marketing.visual.queued",
                JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = taskRuns.claimNext(TYPE, workerId,
            Duration.ofMinutes(6), Duration.ofMinutes(5));
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            visuals.publish(context.projectId(), context.taskRunId(), "INPUT_VALIDATING",
                "job.marketing.visual.input_validating", JobEvent.Status.RUNNING, null);
            var resolved = visuals.resolveExecutionInput(context);
            visuals.publish(context.projectId(), context.taskRunId(), "VISUAL_GENERATING",
                "job.marketing.visual.generating", JobEvent.Status.RUNNING, null);
            var response = ai.executeWorkerResolved(context, claim.taskAttemptId(),
                LocalDateTime.now().plusMinutes(5), resolved);
            visuals.publish(context.projectId(), context.taskRunId(), "RESULT_STORING",
                "job.marketing.visual.result_storing", JobEvent.Status.RUNNING, null);
            completion.complete(claim, context, response);
            visuals.publish(context.projectId(), context.taskRunId(), "COMPLETED",
                "job.marketing.visual.completed", JobEvent.Status.COMPLETED, null);
        } catch (ExecutionFailure failure) {
            fail(claim, context, failure.code(), failure.reason(), failure.retryable(), safe(failure.reason()));
        } catch (BusinessException failure) {
            ErrorCode error = failure.getErrorCode();
            if (error == ErrorCode.FILE_STORAGE_FAILED) {
                fail(claim, context, "EXECUTION_FAILED", "ARTIFACT_STORAGE_FAILED", true,
                    "ARTIFACT_STORAGE_FAILED");
            } else if (error == ErrorCode.MARKETING_ASSET_INVALID) {
                fail(claim, context, "EXECUTION_FAILED", "SOURCE_IMAGE_INVALID", false,
                    "SOURCE_IMAGE_INVALID");
            } else if (error == ErrorCode.MARKETING_PROHIBITED_CLAIM) {
                fail(claim, context, "EXECUTION_FAILED", "SAFETY_POLICY_BLOCKED", false,
                    "MARKETING_PROHIBITED_CLAIM");
            } else {
                fail(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false,
                    "AI_RESULT_INVALID");
            }
        } catch (RuntimeException failure) {
            log.warn("Marketing visual worker failed taskRunId={} type={}", context.taskRunId(),
                failure.getClass().getSimpleName());
            fail(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false,
                "AI_RESULT_INVALID");
        }
        return true;
    }

    private void fail(TaskRunService.Claim claim, TaskRunWorkerContext context, String code,
            String reason, boolean retryable, String safeCode) {
        try { completion.fail(claim, code, reason, retryable); }
        finally {
            visuals.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.marketing.visual.failed", JobEvent.Status.FAILED, safeCode);
        }
    }

    private String safe(String reason) {
        return switch (reason) {
            case "SOURCE_IMAGE_INVALID" -> "SOURCE_IMAGE_INVALID";
            case "COPY_GENERATION_FAILED" -> "COPY_GENERATION_FAILED";
            case "IMAGE_GENERATION_FAILED" -> "IMAGE_GENERATION_FAILED";
            case "IMAGE_COMPOSITION_FAILED" -> "IMAGE_COMPOSITION_FAILED";
            case "SAFETY_POLICY_BLOCKED" -> "MARKETING_PROHIBITED_CLAIM";
            case "REQUEST_DEADLINE_EXCEEDED" -> "TASK_TIMEOUT";
            case "DEPENDENCY_RATE_LIMITED" -> "RATE_LIMITED";
            case "AI_CONFIGURATION_INVALID" -> "AI_CONFIGURATION_INVALID";
            default -> "AI_SERVICE_UNAVAILABLE";
        };
    }
}
