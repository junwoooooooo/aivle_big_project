package com.aivle.backend.pipeline.finalreport.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
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
public class FinalBusinessProposalWorker {
    private static final TaskType TYPE = TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final FinalReportService reports;
    private final String workerId = "final-business-proposal-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.final-proposal-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.final-proposal-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            var context = taskRuns.workerContext(id);
            safePublish(context.projectId(), id, "QUEUED", "job.final-report.queued",
                JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        var claim = taskRuns.claimNext(TYPE, workerId, Duration.ofMinutes(8), Duration.ofMinutes(6));
        if (claim == null) return false;
        var context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            safePublish(context.projectId(), context.taskRunId(), "COMPOSING",
                "job.final-report.composing", JobEvent.Status.RUNNING, null);
            var response = ai.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(6));
            String snapshotId = reports.completeProposal(claim, context, response);
            safePublish(context.projectId(), context.taskRunId(), "COMPLETED",
                "job.final-report.completed", JobEvent.Status.COMPLETED, null);
            safeStartReview(context.ownerId(), context.projectId(), snapshotId);
        } catch (ExecutionFailure failure) {
            safeFail(claim, failure.code(), failure.reason(), failure.retryable());
            safePublish(context.projectId(), context.taskRunId(), "FAILED",
                "job.final-report.failed", JobEvent.Status.FAILED, failure.code());
        } catch (IllegalArgumentException failure) {
            log.warn("Final proposal result invalid taskRunId={}", claim.taskRunId());
            safeFail(claim, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
            safePublish(context.projectId(), context.taskRunId(), "FAILED",
                "job.final-report.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
        } catch (RuntimeException failure) {
            log.warn("Final proposal infrastructure failed taskRunId={} type={}", claim.taskRunId(),
                failure.getClass().getSimpleName());
            safeFail(claim, "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
            safePublish(context.projectId(), context.taskRunId(), "FAILED",
                "job.final-report.failed", JobEvent.Status.FAILED, "EXECUTION_FAILED");
        }
        return true;
    }

    void safePublish(Long projectId, String taskRunId, String stage, String key,
            JobEvent.Status status, String code) {
        try { reports.publish(projectId, taskRunId, stage, key, status, code); }
        catch (RuntimeException failure) {
            log.warn("Final proposal event publish skipped taskRunId={} stage={} type={}",
                taskRunId, stage, failure.getClass().getSimpleName());
        }
    }

    private void safeFail(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        try { reports.failProposal(claim, code, reason, retryable); }
        catch (RuntimeException failure) {
            log.warn("Final proposal failure persistence failed taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName());
        }
    }

    void safeStartReview(Long ownerId, Long projectId, String snapshotId) {
        String key = "auto-review:" + snapshotId;
        try { reports.startReview(ownerId, projectId, snapshotId, key, key); }
        catch (RuntimeException failure) {
            log.warn("Final proposal auto review queue skipped snapshotId={} type={}",
                snapshotId, failure.getClass().getSimpleName());
        }
    }
}
