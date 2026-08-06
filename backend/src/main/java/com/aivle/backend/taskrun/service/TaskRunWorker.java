package com.aivle.backend.taskrun.service;

import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Component
public class TaskRunWorker {
    private final TaskRunService service; private final InternalAiExecutionClient client; private final ObjectMapper mapper;
    public TaskRunWorker(TaskRunService service, InternalAiExecutionClient client, ObjectMapper mapper) { this.service = service; this.client = client; this.mapper = mapper; }
    public boolean executeOne(String workerId) {
        return execute(service.claimNext(workerId, Duration.ofSeconds(30), Duration.ofMinutes(2)));
    }
    public boolean executeOne(com.aivle.backend.taskrun.domain.TaskType taskType, String workerId) {
        return execute(service.claimNext(taskType, workerId, Duration.ofMinutes(3), Duration.ofMinutes(2)));
    }
    private boolean execute(TaskRunService.Claim claim) {
        if (claim == null) return false;
        service.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("AI call must run outside a DB transaction");
        try {
            TaskRun run = service.getOwnedForWorker(claim.taskRunId());
            ExecutionResponse response = client.execute(run, claim.taskAttemptId(), java.time.LocalDateTime.now().plusMinutes(2));
            try {
                validateResult(run, response.result());
            } catch (ExecutionFailure invalidResult) {
                String safePayload = response.result() == null ? "{}" : mapper.writeValueAsString(response.result());
                service.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(), safePayload,
                    response.resultSchemaVersion() == null ? "1.0" : response.resultSchemaVersion(), invalidResult.reason());
                return true;
            }
            service.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(), mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        } catch (ExecutionFailure failure) {
            if ("RESULT_SCHEMA_INVALID".equals(failure.code()))
                service.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{}", "1.0", failure.reason());
            else service.failWithLegalAutoRetry(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
        } catch (TaskRunFailure failure) {
            service.failWithLegalAutoRetry(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "RESULT_SCHEMA_INVALID", failure.getReason(), false);
        } catch (RuntimeException failure) {
            service.failWithLegalAutoRetry(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
        }
        return true;
    }

    private void validateResult(TaskRun run, tools.jackson.databind.JsonNode result) {
        if (run.getTaskType() == com.aivle.backend.taskrun.domain.TaskType.IDEA_LEGAL_PRECHECK
            || run.getTaskType() == com.aivle.backend.taskrun.domain.TaskType.CONCEPT_LEGAL_VALIDATION) {
            com.aivle.backend.taskrun.contract.LegalSourcePipelineContract.validate(result, run.getTaskType().name());
            rejectForbiddenFields(result);
            return;
        }
        if (run.getTaskType() != com.aivle.backend.taskrun.domain.TaskType.IDEA_INTERPRETATION)
            throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_DOMAIN_INVARIANT_VIOLATION", false);
        java.util.Set<String> expected = java.util.Set.of("originalSourceSummary", "normalizedDescription",
            "facts", "assumptions", "constraints", "openQuestions", "readiness", "warnings",
            "evidenceNeeds", "provenance");
        if (!result.isObject() || !java.util.Set.copyOf(result.propertyNames()).equals(expected)
            || !result.get("originalSourceSummary").isTextual() || !result.get("normalizedDescription").isTextual()
            || !java.util.Set.of("UNDER_SPECIFIED", "APPROPRIATE", "OVER_SPECIFIED").contains(result.path("readiness").asText())
            || !result.get("facts").isArray() || !result.get("assumptions").isArray()
            || !result.get("constraints").isArray() || !result.get("openQuestions").isArray()
            || !result.get("warnings").isArray() || !result.get("evidenceNeeds").isArray()
            || !result.get("provenance").isArray() || result.get("provenance").isEmpty())
            throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", false);
        rejectForbiddenFields(result);
    }

    private void rejectForbiddenFields(tools.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            java.util.Set<String> forbidden = java.util.Set.of("storageUrl", "objectKey", "presignedUrl",
                "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential");
            for (String name : node.propertyNames()) {
                if (forbidden.contains(name)) throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_UNKNOWN_FIELD", false);
                rejectForbiddenFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::rejectForbiddenFields);
        }
    }
}
