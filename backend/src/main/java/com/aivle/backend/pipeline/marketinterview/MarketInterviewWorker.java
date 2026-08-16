package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.contract.MarketInterviewContract;
import com.aivle.backend.taskrun.domain.TaskRun;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MarketInterviewWorker {
    private static final TaskType TYPE = TaskType.MARKET_INTERVIEW;
    private static final Duration BUDGET = Duration.ofMinutes(5);
    private static final Duration LEASE = BUDGET.plusMinutes(2);
    private static final Set<String> FORBIDDEN_FIELDS = Set.of("storageUrl", "objectKey", "presignedUrl",
        "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential", "evidenceId");

    private final TaskRunService service;
    private final InternalAiExecutionClient client;
    private final MarketInterviewService completion;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;
    private final String workerId = "market-interview-" + UUID.randomUUID();

    public MarketInterviewWorker(TaskRunService service, InternalAiExecutionClient client,
            MarketInterviewService completion, JobEventPublisher events, ObjectMapper mapper) {
        this.service = service; this.client = client; this.completion = completion;
        this.events = events; this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.task-run.market-interview-poll-interval-ms:2000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.market-interview-recovery-interval-ms:15000}")
    public void recover() {
        for (String id : service.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            publish(service.workerContext(id), "QUEUED", "job.market-interview.queued", JobEvent.Status.QUEUED, null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = service.claimNext(TYPE, workerId, LEASE, BUDGET);
        if (claim == null) return false;
        TaskRunWorkerContext context = service.workerContext(claim.taskRunId());
        service.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI call must run outside a DB transaction");
        publish(context, "INTERVIEWING", "job.market-interview.running", JobEvent.Status.RUNNING, null);
        try {
            TaskRun run = service.getOwnedForWorker(claim.taskRunId());
            ExecutionResponse response = client.execute(run, claim.taskAttemptId(), LocalDateTime.now().plus(BUDGET));
            try {
                MarketInterviewContract.validate(response.result());
                rejectForbiddenFields(response.result());
            } catch (ExecutionFailure invalidResult) {
                String payload = response.result() == null ? "{}" : mapper.writeValueAsString(response.result());
                service.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(), payload,
                    response.resultSchemaVersion() == null ? "1.0" : response.resultSchemaVersion(), invalidResult.reason());
                completion.materializeFailure(run.getId(), "RESULT_SCHEMA_INVALID");
                publish(context, "FAILED", "job.market-interview.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
                return true;
            }
            completion.complete(claim, response);
            publish(context, "COMPLETED", "job.market-interview.completed", JobEvent.Status.COMPLETED, null);
        } catch (ExecutionFailure failure) {
            if ("RESULT_SCHEMA_INVALID".equals(failure.code()))
                service.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{}", "1.0", failure.reason());
            else service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
            completion.materializeFailure(claim.taskRunId(), failure.code());
            publish(context, "FAILED", "job.market-interview.failed", JobEvent.Status.FAILED, "AI_SERVICE_UNAVAILABLE");
        } catch (TaskRunFailure failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "RESULT_SCHEMA_INVALID", failure.getReason(), false);
            completion.materializeFailure(claim.taskRunId(), "RESULT_SCHEMA_INVALID");
            publish(context, "FAILED", "job.market-interview.failed", JobEvent.Status.FAILED, "AI_RESULT_INVALID");
        } catch (RuntimeException failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
            completion.materializeFailure(claim.taskRunId(), "EXECUTION_FAILED");
            publish(context, "FAILED", "job.market-interview.failed", JobEvent.Status.FAILED, "AI_SERVICE_UNAVAILABLE");
        }
        return true;
    }

    private void publish(TaskRunWorkerContext context, String stage, String key,
            JobEvent.Status status, String code) {
        if (context == null) return;
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(),
            context.taskRunId(), stage, key, status, key, Map.of(), code));
    }

    private void rejectForbiddenFields(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (FORBIDDEN_FIELDS.contains(name))
                    throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_UNKNOWN_FIELD", false);
                rejectForbiddenFields(node.get(name));
            }
        } else if (node.isArray()) node.forEach(this::rejectForbiddenFields);
    }
}
