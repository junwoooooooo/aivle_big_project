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
import java.time.Clock;
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
    private final Clock clock;
    private final String workerId = "concept-portfolio-" + UUID.randomUUID();

    public ConceptPortfolioWorker(TaskRunService taskRuns, InternalAiExecutionClient ai,
            ConceptPortfolioMaterializationService materialization, JobEventPublisher events,
            ConceptPortfolioExecutionProperties properties,
            @Qualifier("conceptPortfolioAiExecutor") ExecutorService executor, Clock clock) {
        this.taskRuns = taskRuns; this.ai = ai; this.materialization = materialization;
        this.events = events; this.properties = properties; this.executor = executor;
        this.clock = clock;
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
            LocalDateTime deadline = LocalDateTime.now(clock).plus(properties.aiDeadline());
            future = executor.submit(() -> ai.executeWorker(context, claim.taskAttemptId(), deadline));
            ExecutionResponse response = awaitWithHeartbeat(claim, future);
            if (response == null) return true;
            publish(context, "MATERIALIZING", "job.concept-portfolio.materializing",
                JobEvent.Status.RUNNING, null);
            ConceptPortfolioRunStatus status = materialization.complete(claim, context, response);
            publishSummary(context, response);
            if (status == ConceptPortfolioRunStatus.NEEDS_INPUT) {
                publish(context, "NEEDS_INPUT", "job.concept-portfolio.needs-input",
                    JobEvent.Status.NEEDS_INPUT, null);
            } else if (status == ConceptPortfolioRunStatus.FAILED) {
                publishFailure(context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
            } else {
                publish(context, "COMPLETED", "job.concept-portfolio.completed",
                    JobEvent.Status.COMPLETED, null);
            }
        } catch (ExecutionFailure failure) {
            terminalFailure(claim, context, failure.code(), failure.reason(), failure.retryable(),
                failure.validationFields(), failure.retryAfterMillis());
        } catch (ContractViolation failure) {
            try {
                materialization.failContract(claim, context, null);
                publishFailure(context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
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
        terminalFailure(claim, context, code, reason, retryable, List.of(), null);
    }

    private void terminalFailure(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable,
            List<InternalAiExecutionClient.ValidationIssue> validationFields,
            Long retryAfterMillis) {
        try {
            materialization.failExecution(claim, context, code, reason, retryable);
            publishFailure(context, code, reason, retryable, validationFields, retryAfterMillis);
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

    private void publishFailure(TaskRunWorkerContext context, String code, String reason,
            boolean retryable) {
        publishFailure(context, code, reason, retryable, List.of(), null);
    }

    private void publishFailure(TaskRunWorkerContext context, String code, String reason,
            boolean retryable, List<InternalAiExecutionClient.ValidationIssue> validationFields,
            Long retryAfterMillis) {
        String safeCode = safeIdentifier(code, "EXECUTION_FAILED");
        String safeReason = safeIdentifier(reason, "UNEXPECTED_EXECUTION_FAILURE");
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("failureCode", safeCode);
        params.put("failureReason", safeReason);
        params.put("retryable", retryable);
        if (retryAfterMillis != null && retryAfterMillis >= 0) {
            params.put("retryAfterMillis", retryAfterMillis);
        }
        if (validationFields != null && !validationFields.isEmpty()) {
            params.put("validationFields", validationFields.stream().limit(5).map(issue -> {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("path", bounded(issue.path(), 160));
                item.put("category", bounded(issue.category(), 80));
                item.put("expectedType", bounded(issue.expectedType(), 80));
                return item;
            }).toList());
        }
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), "FAILED", "job.concept-portfolio.failed",
            JobEvent.Status.FAILED, "job.concept-portfolio.failed", params, safeCode));
    }

    private String safeIdentifier(String value, String fallback) {
        return value != null && value.matches("[A-Z][A-Z0-9_.-]{0,79}") ? value : fallback;
    }

    private String bounded(String value, int max) {
        if (value == null) return "";
        String normalized = value.strip();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private void publish(TaskRunWorkerContext context, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), stage, key, status, key, Map.of(), code));
    }

    private void publishSummary(TaskRunWorkerContext context, ExecutionResponse response) {
        var result = response.result();
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("prepared", result.path("producedConceptCount").asInt(0));
        params.put("needsInput", result.path("requiredInputs").isArray()
            ? result.path("requiredInputs").size() : 0);
        params.put("excluded", result.path("preLegalExclusions").isArray()
            ? result.path("preLegalExclusions").size() : 0);
        var summary = result.path("runSummary");
        if (summary.path("candidateGenerated").isIntegralNumber()) {
            params.put("reviewed", summary.path("candidateGenerated").asInt());
        }
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), "SUMMARY", "job.concept-portfolio.summary",
            JobEvent.Status.RUNNING, "job.concept-portfolio.summary", params, null));
    }
}
