package com.aivle.backend.pipeline.conceptportfolio.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioMaterializationService;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioResultContract.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConceptPortfolioWorker {
    private static final TaskType TYPE = TaskType.CONCEPT_PORTFOLIO_V2_RUN;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final ConceptPortfolioMaterializationService materialization;
    private final JobEventPublisher events;
    private final ConceptPortfolioExecutionProperties properties;
    private final ExecutorService executor;
    private final String workerId = "concept-portfolio-" + UUID.randomUUID();

    public ConceptPortfolioWorker(TaskRunService taskRuns, InternalAiExecutionClient ai,
            ConceptPortfolioMaterializationService materialization, JobEventPublisher events,
            ConceptPortfolioExecutionProperties properties,
            @Qualifier("conceptPortfolioAiExecutor") ExecutorService executor) {
        this.taskRuns = taskRuns; this.ai = ai; this.materialization = materialization;
        this.events = events; this.properties = properties; this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.task-run.concept-portfolio.poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.concept-portfolio.recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            publish(taskRuns.workerContext(id), "QUEUED", "job.concept-portfolio.queued",
                JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = taskRuns.claimNext(TYPE, workerId,
            properties.lease(), properties.taskTimeout());
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        Future<ExecutionResponse> future = null;
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            materialization.markRunning(context.subjectId());
            publish(context, "RUNNING", "job.concept-portfolio.running", JobEvent.Status.RUNNING, null);
            publish(context, "AI_EXECUTING", "job.concept-portfolio.ai-executing",
                JobEvent.Status.RUNNING, null);
            LocalDateTime deadline = LocalDateTime.now().plus(properties.aiDeadline());
            future = executor.submit(() -> ai.executeWorker(context, claim.taskAttemptId(), deadline));
            ExecutionResponse response = awaitWithHeartbeat(claim, future);
            if (response == null) return true;
            publish(context, "MATERIALIZING", "job.concept-portfolio.materializing",
                JobEvent.Status.RUNNING, null);
            ConceptPortfolioRunStatus status = materialization.complete(claim, context, response);
            if (status == ConceptPortfolioRunStatus.NEEDS_INPUT) {
                publish(context, "NEEDS_INPUT", "job.concept-portfolio.needs-input",
                    JobEvent.Status.NEEDS_INPUT, null);
            } else if (status == ConceptPortfolioRunStatus.FAILED) {
                publish(context, "FAILED", "job.concept-portfolio.failed",
                    JobEvent.Status.FAILED, "AI_RESULT_INVALID");
            } else {
                publish(context, "COMPLETED", "job.concept-portfolio.completed",
                    JobEvent.Status.COMPLETED, null);
            }
        } catch (ExecutionFailure failure) {
            terminalFailure(claim, context, failure.code(), failure.reason(), failure.retryable());
        } catch (ContractViolation failure) {
            try {
                materialization.failContract(claim, context, null);
                publish(context, "FAILED", "job.concept-portfolio.failed",
                    JobEvent.Status.FAILED, "AI_RESULT_INVALID");
            } catch (TaskRunFailure stale) {
                logAuthorityLoss(context, stale);
            }
        } catch (TaskRunFailure stale) {
            if (authorityFailure(stale)) logAuthorityLoss(context, stale);
            else terminalFailure(claim, context, stale.getCode(), stale.getReason(), stale.isRetryable());
        } catch (RejectedExecutionException saturated) {
            terminalFailure(claim, context, "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
        } catch (RuntimeException failure) {
            log.warn("Concept Portfolio worker failed taskRunId={} type={}",
                claim.taskRunId(), failure.getClass().getSimpleName(), failure);
            terminalFailure(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
        } finally {
            if (future != null && !future.isDone()) future.cancel(true);
        }
        return true;
    }

    private ExecutionResponse awaitWithHeartbeat(TaskRunService.Claim claim,
            Future<ExecutionResponse> future) {
        while (true) {
            try {
                return future.get(properties.heartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException stillRunning) {
                try {
                    taskRuns.heartbeat(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                        properties.lease());
                } catch (RuntimeException authorityLost) {
                    future.cancel(true);
                    log.debug("Concept Portfolio authority lost during heartbeat taskRunId={}",
                        claim.taskRunId());
                    return null;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return null;
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause();
                if (cause instanceof ExecutionFailure executionFailure) throw executionFailure;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("AI execution future failed", cause);
            }
        }
    }

    private void terminalFailure(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        try {
            materialization.failExecution(claim, context, code, reason, retryable);
            publish(context, "FAILED", "job.concept-portfolio.failed", JobEvent.Status.FAILED,
                safeCode(code));
        } catch (TaskRunFailure stale) {
            logAuthorityLoss(context, stale);
        }
    }

    private boolean authorityFailure(TaskRunFailure failure) {
        return "STALE_CLAIM".equals(failure.getReason())
            || "LATE_OR_DUPLICATE_RESULT".equals(failure.getReason());
    }

    private void logAuthorityLoss(TaskRunWorkerContext context, TaskRunFailure failure) {
        log.debug("Concept Portfolio late/stale result ignored taskRunId={} reason={}",
            context.taskRunId(), failure.getReason());
    }

    private String safeCode(String code) {
        if ("DEADLINE_EXCEEDED".equals(code)) return "TASK_TIMEOUT";
        if ("RESULT_SCHEMA_INVALID".equals(code)) return "AI_RESULT_INVALID";
        return "AI_SERVICE_UNAVAILABLE";
    }

    private void publish(TaskRunWorkerContext context, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), stage, key, status, key, Map.of(), code));
    }
}
