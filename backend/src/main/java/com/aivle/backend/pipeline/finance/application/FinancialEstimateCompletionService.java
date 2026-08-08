package com.aivle.backend.pipeline.finance.application;

import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class FinancialEstimateCompletionService {
    private static final String STALE = "STALE_ACTION_RESULT";
    private static final java.util.Set<String> TARGET_METRICS = java.util.Set.of(
        "salesVolume", "customerCount", "subscriberCount", "transactionCount");
    private final FinancialInputPreparationRepository preparations;
    private final FinancialInputSnapshotRepository snapshots;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;

    @Transactional
    public boolean start(TaskRunService.Claim claim, TaskRunWorkerContext context) {
        FinancialInputPreparation preparation = locked(context);
        JsonNode input = input(context);
        if (!fresh(preparation, context, input)) {
            stale(claim, preparation, context, input);
            return false;
        }
        updateStatus(preparation, input, context.taskRunId(), "RUNNING", null);
        return true;
    }

    @Transactional
    public Outcome complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        FinancialInputPreparation preparation = locked(context);
        JsonNode input = input(context);
        if (!fresh(preparation, context, input)) {
            stale(claim, preparation, context, input);
            return Outcome.STALE;
        }
        String fieldKey = input.path("fieldKey").asText();
        JsonNode result = response.result();
        validateResult(fieldKey, result);
        if (input.path("proposalVersion").asInt() > 1
                && same(result.path("proposedValue"), rejected(input), fieldKey)) {
            throw new IllegalStateException("Finance alternative must differ from rejected proposal");
        }
        ObjectNode assistance = (ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        ObjectNode proposal = (ObjectNode) assistance.path(fieldKey);
        proposal.set("proposalValue", result.path("proposedValue").deepCopy());
        proposal.set("assumptions", result.path("assumptions").deepCopy());
        proposal.put("explanation", result.path("explanation").asText());
        proposal.put("confidence", result.path("confidence").asText());
        proposal.put("source", "AI_ESTIMATE");
        proposal.put("decision", "PROPOSED");
        proposal.put("proposalVersion", input.path("proposalVersion").asInt());
        proposal.put("estimateStatus", "SUCCEEDED");
        proposal.putNull("activeTaskRunId");
        proposal.putNull("safeError");
        preparation.updateAssistance(mapper.writeValueAsString(assistance), context.ownerId());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(result), response.canonicalInputHash(), response.resultSchemaVersion());
        return Outcome.SUCCEEDED;
    }

    @Transactional
    public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        FinancialInputPreparation preparation = locked(context);
        JsonNode input = input(context);
        if (matches(preparation, context, input)) {
            updateStatus(preparation, input, null, "FAILED", safeError(code, reason));
        }
    }

    private boolean fresh(FinancialInputPreparation preparation, TaskRunWorkerContext context, JsonNode input) {
        if (!matches(preparation, context, input)
                || preparation.getRevision() != input.path("expectedPreparationRevision").asInt()
                || !preparation.getSourceTechOpsSnapshotId().equals(input.path("sourceTechOpsSnapshotId").asText())
                || !preparation.getSourceSnapshotHash().equals(input.path("sourceSnapshotHash").asText())
                || snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(
                    preparation.getId(), context.projectId()).isPresent()) return false;
        JsonNode field = mapper.readTree(preparation.getFinancialFieldsJson()).path(input.path("fieldKey").asText());
        return field.isObject() && !field.path("readOnly").asBoolean(false);
    }

    private boolean matches(FinancialInputPreparation preparation, TaskRunWorkerContext context, JsonNode input) {
        String fieldKey = input.path("fieldKey").asText();
        if (!FinancialPreparationFactory.ALL_KEYS.contains(fieldKey) || "newCustomerCount".equals(fieldKey)
                || !preparation.getId().equals(input.path("preparationId").asText())) return false;
        JsonNode proposal = mapper.readTree(preparation.getAssistanceJson()).path(fieldKey);
        return context.taskRunId().equals(proposal.path("activeTaskRunId").asText())
            && ("QUEUED".equals(proposal.path("estimateStatus").asText())
                || "RUNNING".equals(proposal.path("estimateStatus").asText()));
    }

    private void stale(TaskRunService.Claim claim, FinancialInputPreparation preparation,
            TaskRunWorkerContext context, JsonNode input) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            "EXECUTION_FAILED", STALE, false);
        if (matches(preparation, context, input)) updateStatus(preparation, input, null, "FAILED", STALE);
    }

    private void updateStatus(FinancialInputPreparation preparation, JsonNode input,
            String activeTaskRunId, String status, String safeError) {
        ObjectNode assistance = (ObjectNode) mapper.readTree(preparation.getAssistanceJson());
        ObjectNode proposal = (ObjectNode) assistance.path(input.path("fieldKey").asText());
        proposal.put("estimateStatus", status);
        if (activeTaskRunId == null) proposal.putNull("activeTaskRunId");
        else proposal.put("activeTaskRunId", activeTaskRunId);
        if (safeError == null) proposal.putNull("safeError");
        else proposal.put("safeError", safeError);
        preparation.updateAssistance(mapper.writeValueAsString(assistance), input.path("ownerId").asLong(0L) > 0
            ? input.path("ownerId").asLong() : preparation.getUpdatedByUserId());
    }

    private void validateResult(String fieldKey, JsonNode result) {
        if (!fieldKey.equals(result.path("fieldKey").asText())
                || !"AI_ESTIMATE".equals(result.path("source").asText())
                || !result.path("assumptions").isArray() || result.path("assumptions").isEmpty()
                || result.path("explanation").asText("").isBlank()
                || !("LOW".equals(result.path("confidence").asText())
                    || "MEDIUM".equals(result.path("confidence").asText())
                    || "HIGH".equals(result.path("confidence").asText()))) {
            throw new IllegalStateException("Finance estimate result is invalid");
        }
        for (JsonNode assumption : result.path("assumptions")) {
            if (!assumption.isTextual() || assumption.asText().isBlank()) {
                throw new IllegalStateException("Finance estimate assumptions are invalid");
            }
        }
        validateValue(fieldKey, result.path("proposedValue"));
    }

    private void validateValue(String fieldKey, JsonNode value) {
        if ("threeYearTargets".equals(fieldKey)) {
            if (!value.isObject() || !TARGET_METRICS.contains(value.path("metric").asText())
                    || value.path("unit").asText("").isBlank() || !value.path("years").isArray()
                    || value.path("years").size() != 3) throw new IllegalStateException("Finance target estimate is invalid");
            boolean[] years = new boolean[4];
            for (JsonNode item : value.path("years")) {
                int year = item.path("year").asInt(-1);
                if (year < 1 || year > 3 || years[year] || !item.path("value").isNumber()
                        || item.path("value").asDouble() < 0) throw new IllegalStateException("Finance target estimate is invalid");
                years[year] = true;
            }
            return;
        }
        if (!value.isObject() || !value.path("amount").isNumber() || value.path("amount").asDouble() < 0
                || value.path("currency").asText("").isBlank()) {
            throw new IllegalStateException("Finance money estimate is invalid");
        }
    }

    private JsonNode rejected(JsonNode input) {
        String json = input.path("rejectedProposalJson").asText("");
        return json.isBlank() ? mapper.nullNode() : mapper.readTree(json);
    }

    private boolean same(JsonNode proposed, JsonNode previous, String fieldKey) {
        if (previous == null || previous.isNull() || previous.isMissingNode()) return false;
        if (new SnapshotHasher(mapper).hash(proposed).equals(new SnapshotHasher(mapper).hash(previous))) return true;
        if (!"threeYearTargets".equals(fieldKey)) {
            return proposed.path("amount").decimalValue().compareTo(previous.path("amount").decimalValue()) == 0
                && normalized(proposed.path("currency")).equals(normalized(previous.path("currency")));
        }
        if (!normalized(proposed.path("metric")).equals(normalized(previous.path("metric")))
                || !normalized(proposed.path("unit")).equals(normalized(previous.path("unit")))) return false;
        for (int year = 1; year <= 3; year++) {
            JsonNode proposedYear = yearValue(proposed.path("years"), year);
            JsonNode previousYear = yearValue(previous.path("years"), year);
            if (!proposedYear.isNumber() || !previousYear.isNumber()
                    || proposedYear.decimalValue().compareTo(previousYear.decimalValue()) != 0) return false;
        }
        return true;
    }

    private JsonNode yearValue(JsonNode years, int year) {
        for (JsonNode item : years) if (item.path("year").asInt(-1) == year) return item.path("value");
        return mapper.missingNode();
    }

    private String normalized(JsonNode value) {
        return value.asText("").strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private FinancialInputPreparation locked(TaskRunWorkerContext context) {
        return preparations.findLocked(context.subjectId(), context.projectId())
            .orElseThrow(() -> new IllegalStateException("Finance preparation is missing"));
    }

    private JsonNode input(TaskRunWorkerContext context) {
        JsonNode input = mapper.readTree(context.inputSnapshot());
        if (!input.isObject()) throw new IllegalStateException("Finance estimate input is invalid");
        return input;
    }

    private String safeError(String code, String reason) {
        if (STALE.equals(reason)) return STALE;
        if ("DEADLINE_EXCEEDED".equals(code)) return "TASK_TIMEOUT";
        if ("RATE_LIMITED".equals(code)) return "RATE_LIMITED";
        return "AI_SERVICE_UNAVAILABLE";
    }

    public enum Outcome { SUCCEEDED, STALE }
}
