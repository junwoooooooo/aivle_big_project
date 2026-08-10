package com.aivle.backend.taskrun.integration;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class InternalAiExecutionClient {
    private static final Logger log = LoggerFactory.getLogger(InternalAiExecutionClient.class);
    public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final Set<String> SUCCESS_FIELDS = Set.of(
        "contractVersion", "taskType", "taskSchemaVersion", "taskRunId", "taskAttemptId",
        "correlationId", "canonicalInputHash", "resultSchemaVersion", "result", "warnings",
        "provenance", "usage"
    );
    private static final Set<String> INTERNAL_CODES = Set.of(
        "INVALID_REQUEST", "UNAUTHORIZED_INTERNAL_CALL", "UNSUPPORTED_CONTRACT_VERSION",
        "UNSUPPORTED_TASK_TYPE", "UNSUPPORTED_TASK_SCHEMA_VERSION", "PAYLOAD_TOO_LARGE",
        "DEADLINE_EXCEEDED", "DEPENDENCY_UNAVAILABLE", "RATE_LIMITED", "EXECUTION_FAILED",
        "RESULT_SCHEMA_INVALID", "INTERNAL_ERROR"
    );
    private static final Map<String, Set<String>> ERROR_REASONS = Map.ofEntries(
        Map.entry("INVALID_REQUEST", Set.of("JSON_PARSE_FAILED", "HEADER_BODY_CORRELATION_MISMATCH",
            "UNKNOWN_FIELD", "FIELD_CONSTRAINT_VIOLATION", "HASH_MISMATCH", "CHUNK_SEQUENCE_INVALID",
            "REFERENCE_RESOLUTION_FAILED", "LEGAL_INPUT_CONTRACT_INCOMPLETE", "LEGAL_MODE_INVALID",
            "LEGAL_RERUN_CATEGORIES_INVALID", "LEGAL_CONFIRMED_FACTS_INVALID",
            "LEGAL_REGISTRY_VERSION_MISMATCH", "CONCEPT_LEGAL_VALIDATION_MODE_INVALID",
            "BOUNDARY_INPUT_CONTRACT_INCOMPLETE", "CONCEPT_EXPLORATION_INPUT_INVALID",
            "CONCEPT_FAILURE_INJECTION_DISABLED")),
        Map.entry("UNAUTHORIZED_INTERNAL_CALL", Set.of("SERVICE_TOKEN_MISSING", "SERVICE_TOKEN_INVALID",
            "INTERNAL_PRINCIPAL_FORBIDDEN")),
        Map.entry("UNSUPPORTED_CONTRACT_VERSION", Set.of("CONTRACT_VERSION_UNSUPPORTED")),
        Map.entry("UNSUPPORTED_TASK_TYPE", Set.of("TASK_TYPE_UNSUPPORTED")),
        Map.entry("UNSUPPORTED_TASK_SCHEMA_VERSION", Set.of("TASK_SCHEMA_VERSION_UNSUPPORTED")),
        Map.entry("PAYLOAD_TOO_LARGE", Set.of("REQUEST_BYTES_EXCEEDED", "RESPONSE_BYTES_EXCEEDED",
            "TEXT_CONTENT_COUNT_EXCEEDED", "CHUNK_COUNT_EXCEEDED", "CHUNK_CHARACTERS_EXCEEDED",
            "TOTAL_CHARACTERS_EXCEEDED", "TASK_COLLECTION_LIMIT_EXCEEDED")),
        Map.entry("DEADLINE_EXCEEDED", Set.of("REQUEST_DEADLINE_EXCEEDED")),
        Map.entry("DEPENDENCY_UNAVAILABLE", Set.of("MODEL_DEPENDENCY_UNAVAILABLE", "MCP_DEPENDENCY_UNAVAILABLE",
            "LEGAL_SOURCE_DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", "LEGAL_CONFIGURATION_INVALID",
            "MOLEG_AUTHENTICATION_FAILED", "MOLEG_REQUEST_REJECTED", "MOLEG_RESPONSE_INVALID",
            "MOLEG_DEPENDENCY_UNAVAILABLE", "MOLEG_RATE_LIMITED")),
        Map.entry("RATE_LIMITED", Set.of("DEPENDENCY_RATE_LIMITED")),
        Map.entry("EXECUTION_FAILED", Set.of("TRANSIENT_EXECUTION_FAILURE", "PERMANENT_EXECUTION_FAILURE",
            "SAFETY_POLICY_BLOCKED")),
        Map.entry("RESULT_SCHEMA_INVALID", Set.of("RESULT_UNKNOWN_FIELD", "RESULT_FIELD_CONSTRAINT_VIOLATION",
            "RESULT_REFERENCE_INVALID", "RESULT_DOMAIN_INVARIANT_VIOLATION", "AI_RESULT_INVALID",
            "PROVIDER_RESPONSE_SCHEMA_REJECTED", "PROVIDER_JSON_INVALID",
            "PYDANTIC_RESULT_VALIDATION_FAILED", "LOCKED_VALUE_MISMATCH",
            "GOVERNANCE_SEMANTICS_MISMATCH", "VALUE_SEMANTICS_INCOMPLETE",
            "CANDIDATE_METADATA_INVALID", "CONTENT_FIELD_MISSING",
            "LEGAL_JURISDICTION_UNSUPPORTED",
            "LEGAL_ROUTING_CONTRACT_INVALID", "LEGAL_SCREENING_CONTRACT_INVALID",
            "LEGAL_CITATION_COVERAGE_INVALID", "LEGAL_SCREENING_FIELD_INVALID", "LEGAL_SOURCE_CONTRACT_INVALID",
            "CONCEPT_LEGAL_VALIDATION_INVALID", "CONCEPT_LEGAL_VALIDATION_CANDIDATE_KEYS_INVALID",
            "EVIDENCE_REFERENCE_INVALID", "CONCEPT_LEGAL_FINDING_EVIDENCE_REQUIRED",
            "CONCEPT_LEGAL_EVIDENCE_REQUIRED",
            "FINAL_SYNTHESIS_QUESTIONS_FORBIDDEN",
            "BOUNDARY_NORMALIZATION_CONTRACT_INVALID", "BOUNDARY_EVIDENCE_REFERENCE_INVALID",
            "BOUNDARY_EVIDENCE_REQUIRED", "BOUNDARY_RULE_NOT_NORMALIZED", "BOUNDARY_REFERENCE_INVALID",
            "BOUNDARY_LOCKED_CONFLICT_INVALID", "CONCEPT_UNKNOWN_RULE_TYPE",
            "CONCEPT_EXPLORATION_RESULT_INVALID")),
        Map.entry("INTERNAL_ERROR", Set.of("UNEXPECTED_INTERNAL_ERROR"))
    );
    private static final Set<String> RETRYABLE_REASONS = Set.of(
        "REQUEST_DEADLINE_EXCEEDED", "MODEL_DEPENDENCY_UNAVAILABLE", "MCP_DEPENDENCY_UNAVAILABLE",
        "LEGAL_SOURCE_DEPENDENCY_UNAVAILABLE", "MOLEG_DEPENDENCY_UNAVAILABLE", "MOLEG_RATE_LIMITED",
        "DEPENDENCY_RATE_LIMITED", "TRANSIENT_EXECUTION_FAILURE", "UNEXPECTED_INTERNAL_ERROR"
    );

    private final RestClient client;
    private final RestClient conceptPortfolioClient;
    private final AiServerProperties properties;
    private final ObjectMapper mapper;

    @Autowired
    public InternalAiExecutionClient(@Qualifier("aiServerRestClient") RestClient client,
            @Qualifier("conceptPortfolioAiServerRestClient") RestClient conceptPortfolioClient,
            AiServerProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.conceptPortfolioClient = conceptPortfolioClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    public InternalAiExecutionClient(RestClient client, AiServerProperties properties, ObjectMapper mapper) {
        this(client, client, properties, mapper);
    }

    public ExecutionResponse execute(TaskRun run, String attemptId, LocalDateTime deadline) {
        return execute(ExecutionRequest.from(run), attemptId, deadline);
    }

    public ExecutionResponse executeWorker(TaskRunWorkerContext run, String attemptId, LocalDateTime deadline) {
        return execute(new ExecutionRequest(run.taskRunId(), run.taskType(), run.inputSnapshot(),
            run.inputHash(), run.correlationId(), run.contractVersion(), run.taskSchemaVersion(),
            run.locale()), attemptId, deadline);
    }

    private ExecutionResponse execute(ExecutionRequest run, String attemptId, LocalDateTime deadline) {
        if (properties.internalApiKey() == null || properties.internalApiKey().isBlank())
            throw new ExecutionFailure("UNAUTHORIZED_INTERNAL_CALL", "SERVICE_TOKEN_MISSING", false);
        byte[] requestBytes = mapper.writeValueAsBytes(requestEnvelope(run, attemptId, deadline));
        enforceSize(requestBytes, "REQUEST_BYTES_EXCEEDED");
        try {
            RestClient selectedClient = clientFor(run.taskType());
            byte[] responseBytes = selectedClient.post().uri("/internal/v1/ai/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.internalApiKey())
                .header("X-Correlation-Id", run.correlationId())
                .body(requestBytes).retrieve().body(byte[].class);
            enforceSize(responseBytes, "RESPONSE_BYTES_EXCEEDED");
            return validateResponse(run, attemptId, parseSuccess(responseBytes));
        } catch (RestClientResponseException responseFailure) {
            byte[] responseBytes = responseFailure.getResponseBodyAsByteArray();
            enforceSize(responseBytes, "RESPONSE_BYTES_EXCEEDED");
            throw parseFailure(responseBytes, run.taskType());
        } catch (ResourceAccessException timeoutOrConnectionFailure) {
            throw new ExecutionFailure(
                "DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", true
            );
        }
    }

    RestClient clientFor(TaskType taskType) {
        return taskType == TaskType.CONCEPT_PORTFOLIO_V2_RUN
            || taskType == TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE
            || taskType == TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION
            ? conceptPortfolioClient : client;
    }

    JsonNode requestPayload(TaskRunWorkerContext run, String attemptId, LocalDateTime deadline) {
        return mapper.valueToTree(requestEnvelope(new ExecutionRequest(
            run.taskRunId(), run.taskType(), run.inputSnapshot(), run.inputHash(), run.correlationId(),
            run.contractVersion(), run.taskSchemaVersion(), run.locale()), attemptId, deadline));
    }

    private RequestEnvelope requestEnvelope(ExecutionRequest run, String attemptId, LocalDateTime deadline) {
        return new RequestEnvelope(run.contractVersion(), run.taskType().name(), run.taskSchemaVersion(),
            run.taskRunId(), attemptId, run.correlationId(),
            DateTimeFormatter.ISO_INSTANT.format(deadline.toInstant(ZoneOffset.UTC)),
            run.inputHash(), run.locale(), mapper.readTree(run.inputSnapshot()));
    }

    public ExecutionResponse validateResponse(TaskRun run, String attemptId, ExecutionResponse response) {
        return validateResponse(ExecutionRequest.from(run), attemptId, response);
    }

    private ExecutionResponse validateResponse(ExecutionRequest run, String attemptId, ExecutionResponse response) {
        if (!run.contractVersion().equals(response.contractVersion())
            || !run.taskType().name().equals(response.taskType())
            || !run.taskSchemaVersion().equals(response.taskSchemaVersion())
            || !run.taskRunId().equals(response.taskRunId())
            || !attemptId.equals(response.taskAttemptId())
            || !run.correlationId().equals(response.correlationId())
            || !run.inputHash().equals(response.canonicalInputHash())
            || !"1.0".equals(response.resultSchemaVersion())
            || response.result() == null) {
            throw invalid("RESULT_DOMAIN_INVARIANT_VIOLATION");
        }
        return response;
    }

    private ExecutionResponse parseSuccess(byte[] raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            if (!root.isObject() || !Set.copyOf(root.propertyNames()).equals(SUCCESS_FIELDS))
                throw invalid("RESULT_UNKNOWN_FIELD");
            JsonNode result = root.get("result");
            JsonNode warnings = root.get("warnings");
            JsonNode provenance = root.get("provenance");
            if (result == null || !result.isObject() || warnings == null || !warnings.isArray()
                || provenance == null || !provenance.isArray()) throw invalid("RESULT_FIELD_CONSTRAINT_VIOLATION");
            return new ExecutionResponse(text(root, "contractVersion"), text(root, "taskType"),
                text(root, "taskSchemaVersion"), text(root, "taskRunId"), text(root, "taskAttemptId"),
                text(root, "correlationId"), text(root, "canonicalInputHash"),
                text(root, "resultSchemaVersion"), result, warnings, provenance, root.get("usage"));
        } catch (ExecutionFailure known) {
            throw known;
        } catch (RuntimeException invalidJson) {
            throw invalid("RESULT_FIELD_CONSTRAINT_VIOLATION");
        }
    }

    private ExecutionFailure parseFailure(byte[] raw, TaskType taskType) {
        try {
            JsonNode error = mapper.readTree(raw).get("error");
            String code = text(error, "code");
            boolean retryable = error.has("retryable") && error.get("retryable").isBoolean()
                && error.get("retryable").asBoolean();
            JsonNode details = error.get("details");
            String reason = details != null && details.isArray() && !details.isEmpty()
                ? text(details.get(0), "reason") : "UNEXPECTED_INTERNAL_ERROR";
            if (!INTERNAL_CODES.contains(code) || !ERROR_REASONS.getOrDefault(code, Set.of()).contains(reason)
                || RETRYABLE_REASONS.contains(reason) != retryable)
                return invalid("RESULT_DOMAIN_INVARIANT_VIOLATION");
            List<ValidationIssue> fields = validationIssues(details);
            Long retryAfterMs = retryAfterMillis(details);
            if ("INVALID_REQUEST".equals(code) && !fields.isEmpty()) {
                log.warn("Internal AI request rejected taskType={} code=REQUEST_SCHEMA_INVALID fields={}",
                    taskType.name(), fields);
            }
            return new ExecutionFailure(code, reason, retryable, fields, retryAfterMs);
        } catch (ExecutionFailure known) {
            return known;
        } catch (RuntimeException invalidError) {
            return new ExecutionFailure("INTERNAL_ERROR", "UNEXPECTED_INTERNAL_ERROR", true);
        }
    }

    private List<ValidationIssue> validationIssues(JsonNode details) {
        if (details == null || !details.isArray() || details.isEmpty()) return List.of();
        JsonNode fields = details.get(0).get("fields");
        if (fields == null || !fields.isArray()) return List.of();
        List<ValidationIssue> safe = new ArrayList<>();
        for (JsonNode field : fields) {
            if (safe.size() == 12 || !field.isObject()) break;
            String path = safeDiagnostic(field, "path", 200);
            String expected = safeDiagnostic(field, "expectedType", 80);
            String category = safeDiagnostic(field, "category", 80);
            if (path != null && expected != null && category != null) {
                safe.add(new ValidationIssue(path, expected, category));
            }
        }
        return List.copyOf(safe);
    }

    private Long retryAfterMillis(JsonNode details) {
        if (details == null || !details.isArray() || details.isEmpty()) return null;
        JsonNode value = details.get(0).get("retryAfterMs");
        if (value == null || !value.isIntegralNumber()) return null;
        long milliseconds = value.asLong();
        return milliseconds >= 1_000 && milliseconds <= 15_000 ? milliseconds : null;
    }

    private String safeDiagnostic(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) return null;
        String text = value.asText();
        return text.length() <= maxLength && text.matches("[A-Za-z0-9_.\\[\\] -]+") ? text : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw invalid("RESULT_FIELD_CONSTRAINT_VIOLATION");
        return value.asText();
    }

    private void enforceSize(byte[] bytes, String reason) {
        if (bytes == null || bytes.length > MAX_JSON_BYTES)
            throw new ExecutionFailure("PAYLOAD_TOO_LARGE", reason, false);
    }

    private ExecutionFailure invalid(String reason) {
        return new ExecutionFailure("RESULT_SCHEMA_INVALID", reason, false);
    }

    public static class ExecutionFailure extends RuntimeException {
        private final String code;
        private final String reason;
        private final boolean retryable;
        private final List<ValidationIssue> validationFields;
        private final Long retryAfterMillis;

        public ExecutionFailure(String code, String reason, boolean retryable) {
            this(code, reason, retryable, List.of(), null);
        }

        public ExecutionFailure(String code, String reason, boolean retryable,
                List<ValidationIssue> validationFields) {
            this(code, reason, retryable, validationFields, null);
        }

        public ExecutionFailure(String code, String reason, boolean retryable,
                List<ValidationIssue> validationFields, Long retryAfterMillis) {
            super(code + ":" + reason);
            this.code = code;
            this.reason = reason;
            this.retryable = retryable;
            this.validationFields = List.copyOf(validationFields);
            this.retryAfterMillis = retryAfterMillis;
        }

        public String code() { return code; }
        public String reason() { return reason; }
        public boolean retryable() { return retryable; }
        public List<ValidationIssue> validationFields() { return validationFields; }
        public Long retryAfterMillis() { return retryAfterMillis; }
    }

    public record ValidationIssue(String path, String expectedType, String category) { }

    public record ExecutionResponse(String contractVersion, String taskType, String taskSchemaVersion,
        String taskRunId, String taskAttemptId, String correlationId, String canonicalInputHash,
        String resultSchemaVersion, JsonNode result, JsonNode warnings, JsonNode provenance, JsonNode usage) { }

    private record RequestEnvelope(String contractVersion, String taskType, String taskSchemaVersion,
        String taskRunId, String taskAttemptId, String correlationId, String deadlineAt,
        String canonicalInputHash, String locale, JsonNode input) { }

    private record ExecutionRequest(String taskRunId, TaskType taskType, String inputSnapshot,
            String inputHash, String correlationId, String contractVersion,
            String taskSchemaVersion, String locale) {
        private static ExecutionRequest from(TaskRun run) {
            return new ExecutionRequest(run.getId(), run.getTaskType(), run.getInputSnapshot(),
                run.getInputHash(), run.getCorrelationId(), run.getContractVersion(),
                run.getTaskSchemaVersion(), run.getLocale());
        }
    }
}
