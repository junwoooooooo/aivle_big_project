package com.aivle.backend.pipeline.conceptportfolio.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationMaterializationService;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationResultContract.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioContinuationOutcome;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Clock;
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
public class ConceptPortfolioContinuationWorker {
    private static final TaskType TYPE = TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE;
    private final TaskRunService taskRuns;
    private final InternalAiExecutionClient ai;
    private final ConceptPortfolioContinuationMaterializationService materialization;
    private final JobEventPublisher events;
    private final ConceptPortfolioExecutionProperties properties;
    private final ExecutorService executor;
    private final Clock clock;
    private final String workerId = "concept-portfolio-continuation-" + UUID.randomUUID();

    public ConceptPortfolioContinuationWorker(TaskRunService taskRuns,
            InternalAiExecutionClient ai,
            ConceptPortfolioContinuationMaterializationService materialization,
            JobEventPublisher events, ConceptPortfolioExecutionProperties properties,
            @Qualifier("conceptPortfolioAiExecutor") ExecutorService executor, Clock clock) {
        this.taskRuns = taskRuns; this.ai = ai; this.materialization = materialization;
        this.events = events; this.properties = properties; this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.task-run.concept-portfolio.continuation-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.concept-portfolio.continuation-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            publish(taskRuns.workerContext(id), "QUEUED", JobEvent.Status.QUEUED, null);
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
            publish(context, "RUNNING", JobEvent.Status.RUNNING, null);
            publish(context, "AI_EXECUTING", JobEvent.Status.RUNNING, null);
            LocalDateTime deadline = LocalDateTime.now(clock).plus(properties.aiDeadline());
            future = executor.submit(() -> ai.executeWorker(context, claim.taskAttemptId(), deadline));
            ExecutionResponse response = awaitWithHeartbeat(claim, future);
            if (response == null) return true;
            publish(context, "MATERIALIZING", JobEvent.Status.RUNNING, null);
            ConceptPortfolioContinuationOutcome outcome = materialization.complete(claim, context, response);
            if (outcome == ConceptPortfolioContinuationOutcome.NEEDS_INPUT) {
                publish(context, "NEEDS_INPUT", JobEvent.Status.NEEDS_INPUT, null);
            } else if (outcome == ConceptPortfolioContinuationOutcome.SYSTEM_FAILURE) {
                publish(context, "FAILED", JobEvent.Status.FAILED, "AI_SERVICE_UNAVAILABLE");
            } else {
                publish(context, "COMPLETED", JobEvent.Status.COMPLETED, null);
            }
        } catch (ExecutionFailure failure) {
            terminalFailure(claim, context, failure.code(), failure.reason(), failure.retryable());
        } catch (ContractViolation failure) {
            try {
                materialization.failContract(claim, context, null);
                publish(context, "FAILED", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
            } catch (TaskRunFailure stale) { logAuthorityLoss(context, stale); }
        } catch (TaskRunFailure failure) {
            if (authorityFailure(failure)) logAuthorityLoss(context, failure);
            else terminalFailure(claim, context, failure.getCode(), failure.getReason(),
                failure.isRetryable());
        } catch (RejectedExecutionException saturated) {
            terminalFailure(claim, context, "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
        } catch (RuntimeException failure) {
            log.warn("Concept Portfolio continuation worker failed taskRunId={} type={}",
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
                    log.debug("Continuation authority lost during heartbeat taskRunId={}", claim.taskRunId());
                    return null;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); future.cancel(true); return null;
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause();
                if (cause instanceof ExecutionFailure executionFailure) throw executionFailure;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("AI continuation future failed", cause);
            }
        }
    }

    private void terminalFailure(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        try {
            materialization.failExecution(claim, context, code, reason, retryable);
            publish(context, "FAILED", JobEvent.Status.FAILED, safeCode(code));
        } catch (TaskRunFailure stale) { logAuthorityLoss(context, stale); }
    }

    private boolean authorityFailure(TaskRunFailure failure) {
        return "STALE_CLAIM".equals(failure.getReason())
            || "LATE_OR_DUPLICATE_RESULT".equals(failure.getReason());
    }

    private void logAuthorityLoss(TaskRunWorkerContext context, TaskRunFailure failure) {
        log.debug("Continuation late/stale result ignored taskRunId={} reason={}",
            context.taskRunId(), failure.getReason());
    }

    private String safeCode(String code) {
        if ("DEADLINE_EXCEEDED".equals(code)) return "TASK_TIMEOUT";
        if ("RESULT_SCHEMA_INVALID".equals(code)) return "AI_RESULT_INVALID";
        return "AI_SERVICE_UNAVAILABLE";
    }

    private void publish(TaskRunWorkerContext context, String stage,
            JobEvent.Status status, String code) {
        String key = "job.concept-portfolio.continuation." + stage.toLowerCase().replace('_', '-');
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), stage, key, status, key, Map.of(), code));
    }
}
