package com.aivle.backend.pipeline.finalreport.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalBusinessProposalReviewWorker {
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final FinalReportService reports;
    private final String workerId = "final-business-proposal-review-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.final-proposal-review-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    public boolean processOne() {
        var claim = taskRuns.claimNext(TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW, workerId,
            Duration.ofMinutes(6), Duration.ofMinutes(5));
        if (claim == null) return false;
        var context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            reports.publish(context.projectId(), context.taskRunId(), "REVIEWING",
                "job.final-report.review.running", JobEvent.Status.RUNNING, null);
            var response = ai.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(5));
            reports.completeReview(claim, context, response);
            reports.publish(context.projectId(), context.taskRunId(), "COMPLETED",
                "job.final-report.review.completed", JobEvent.Status.COMPLETED, null);
        } catch (ExecutionFailure failure) {
            reports.failProposal(claim, failure.code(), failure.reason(), failure.retryable());
            reports.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.final-report.review.failed", JobEvent.Status.FAILED, failure.code());
        } catch (RuntimeException failure) {
            reports.failProposal(claim, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
            reports.publish(context.projectId(), context.taskRunId(), "FAILED",
                "job.final-report.review.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
        }
        return true;
    }
}
