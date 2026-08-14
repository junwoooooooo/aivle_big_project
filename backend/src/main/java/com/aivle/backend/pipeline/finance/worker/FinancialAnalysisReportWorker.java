package com.aivle.backend.pipeline.finance.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FinancialAnalysisReportWorker {
    private static final TaskType TYPE = TaskType.FINANCE_ANALYSIS_REPORT;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final FinancialAnalysisService analysis;
    private final String workerId = "finance-analysis-report-" + UUID.randomUUID();

    public FinancialAnalysisReportWorker(TaskRunService taskRuns, InternalAiExecutionClient ai,
            FinancialAnalysisService analysis) {
        this.taskRuns = taskRuns; this.ai = ai; this.analysis = analysis;
    }

    @Scheduled(fixedDelayString = "${app.task-run.finance-analysis-report-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.finance-analysis-report-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            var context = taskRuns.workerContext(id);
            analysis.publish(context.projectId(), context.taskRunId(), "QUEUED",
                "job.finance.analysis.queued", JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        var claim = taskRuns.claimNext(TYPE, workerId, Duration.ofMinutes(5), Duration.ofMinutes(3));
        if (claim == null) return false;
        var context = taskRuns.workerContext(claim.taskRunId());
        taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        analysis.publish(context.projectId(), context.taskRunId(), "REPORTING",
            "job.finance.analysis.reporting", JobEvent.Status.RUNNING, null);
        String fallbackReason = null;
        try {
            var response = ai.executeWorker(context, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(3));
            analysis.complete(claim, context, response);
        } catch (ExecutionFailure failure) {
            fallbackReason = safeReason(failure);
            analysis.completeFallback(claim, context, fallbackReason);
        } catch (RuntimeException failure) {
            log.warn("Finance analysis report worker fallback taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName());
            fallbackReason = "AI_REPORT_INVALID";
            analysis.completeFallback(claim, context, fallbackReason);
        }
        if (fallbackReason == null) {
            analysis.publish(context.projectId(), context.taskRunId(), "COMPLETED",
                "job.finance.analysis.completed", JobEvent.Status.COMPLETED, null);
        } else {
            analysis.publish(context.projectId(), context.taskRunId(), "COMPLETED_WITH_FALLBACK",
                "job.finance.analysis.fallback", JobEvent.Status.COMPLETED, fallbackReason);
        }
        return true;
    }

    private String safeReason(ExecutionFailure failure) {
        if ("DEADLINE_EXCEEDED".equals(failure.code())) return "TASK_TIMEOUT";
        if ("RATE_LIMITED".equals(failure.code())) return "RATE_LIMITED";
        if ("RESULT_SCHEMA_INVALID".equals(failure.code())) return "AI_REPORT_INVALID";
        return "AI_SERVICE_UNAVAILABLE";
    }
}
