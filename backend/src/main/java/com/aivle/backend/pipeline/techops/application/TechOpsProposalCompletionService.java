package com.aivle.backend.pipeline.techops.application;

import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import lombok.RequiredArgsConstructor;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class TechOpsProposalCompletionService {
    private static final String STALE = "STALE_ACTION_RESULT";
    private final TechOpsInputPreparationRepository preparations;
    private final TechOpsInputSnapshotRepository snapshots;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;

    @Transactional
    public boolean start(TaskRunService.Claim claim, TaskRunWorkerContext context) {
        TechOpsInputPreparation preparation = locked(context);
        JsonNode input = input(context);
        if (!fresh(preparation, context, input)) {
            stale(claim, preparation, context, input);
            return false;
        }
        preparation.startProposalTask(context.taskRunId());
        return true;
    }

    @Transactional
    public Outcome complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        TechOpsInputPreparation preparation = locked(context);
        JsonNode input = input(context);
        if (!fresh(preparation, context, input)) {
            stale(claim, preparation, context, input);
            return Outcome.STALE;
        }
        ObjectNode decisions = (ObjectNode) mapper.readTree(preparation.getProposalDecisionsJson());
        if ("INITIAL".equals(input.path("mode").asText())) {
            commitInitial(decisions, response.result());
        } else if ("ALTERNATIVE".equals(input.path("mode").asText())) {
            commitAlternative(decisions, input, response.result());
        } else {
            throw new IllegalStateException("TechOps proposal mode is invalid");
        }
        preparation.completeProposalTask(context.taskRunId(), mapper.writeValueAsString(decisions), "SUCCEEDED");
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        return Outcome.SUCCEEDED;
    }

    @Transactional
    public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        TechOpsInputPreparation preparation = locked(context);
        if (preparation.proposalTaskMatches(context.taskRunId())) {
            JsonNode input = input(context);
            ObjectNode decisions = clearPending(preparation, input);
            preparation.failProposalTask(context.taskRunId(), mapper.writeValueAsString(decisions),
                "FAILED", safeError(code, reason));
        }
    }

    private void commitInitial(ObjectNode decisions, JsonNode result) {
        for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) {
            JsonNode proposal = result.path(key);
            validate(key, proposal);
            ObjectNode field = (ObjectNode) decisions.path(key);
            if (TechOpsPreparationFactory.present(field.path("proposalValue"))) continue;
            field.set("proposalValue", proposal.deepCopy());
            field.putNull("finalValue"); field.put("source", "AI_HYPOTHESIS");
            field.put("decision", "PROPOSED"); field.put("proposalVersion", 1);
            field.put("alternativeRequested", false); field.putNull("pendingAlternativeTaskRunId");
        }
    }

    private void commitAlternative(ObjectNode decisions, JsonNode input, JsonNode result) {
        String key = input.path("fieldKey").asText();
        if (!TechOpsPreparationFactory.PROPOSAL_KEYS.contains(key)) {
            throw new IllegalStateException("TechOps alternative field is invalid");
        }
        ObjectNode field = (ObjectNode) decisions.path(key);
        JsonNode proposal = result.path(key); validate(key, proposal);
        if (sameAlternative(key, proposal, field.path("proposalValue"))) {
            throw new IllegalStateException("TechOps alternative must differ from rejected proposal");
        }
        field.set("proposalValue", proposal.deepCopy()); field.putNull("finalValue");
        field.put("source", "AI_HYPOTHESIS"); field.put("decision", "PROPOSED");
        field.put("proposalVersion", input.path("proposalVersion").asInt());
        field.put("alternativeRequested", false); field.putNull("pendingAlternativeTaskRunId");
    }

    private boolean fresh(TechOpsInputPreparation preparation, TaskRunWorkerContext context, JsonNode input) {
        if (!preparation.proposalTaskMatches(context.taskRunId())
                || preparation.getRevision() != input.path("expectedPreparationRevision").asInt()
                || !preparation.getId().equals(input.path("preparationId").asText())
                || !preparation.getSourceMarketSeedSnapshotId().equals(input.path("sourceMarketSeedSnapshotId").asText())
                || !preparation.getSourceSnapshotHash().equals(input.path("sourceSnapshotHash").asText())
                || snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(
                    preparation.getId(), context.projectId()).isPresent()) return false;
        if (!"ALTERNATIVE".equals(input.path("mode").asText())) return "INITIAL".equals(input.path("mode").asText());
        String key = input.path("fieldKey").asText();
        JsonNode field = mapper.readTree(preparation.getProposalDecisionsJson()).path(key);
        return TechOpsPreparationFactory.PROPOSAL_KEYS.contains(key)
            && field.path("proposalVersion").asInt() == input.path("currentProposalVersion").asInt()
            && context.taskRunId().equals(field.path("pendingAlternativeTaskRunId").asText());
    }

    private void stale(TaskRunService.Claim claim, TechOpsInputPreparation preparation,
            TaskRunWorkerContext context, JsonNode input) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            "EXECUTION_FAILED", STALE, false);
        if (preparation.proposalTaskMatches(context.taskRunId())) {
            ObjectNode decisions = clearPending(preparation, input);
            preparation.failProposalTask(context.taskRunId(), mapper.writeValueAsString(decisions),
                STALE, STALE);
        }
    }

    private ObjectNode clearPending(TechOpsInputPreparation preparation, JsonNode input) {
        ObjectNode decisions = (ObjectNode) mapper.readTree(preparation.getProposalDecisionsJson());
        if ("ALTERNATIVE".equals(input.path("mode").asText())) {
            JsonNode node = decisions.path(input.path("fieldKey").asText());
            if (node.isObject()) {
                ObjectNode field = (ObjectNode) node;
                field.put("alternativeRequested", false); field.putNull("pendingAlternativeTaskRunId");
            }
        }
        return decisions;
    }

    private void validate(String key, JsonNode value) {
        boolean valid = TechOpsPreparationFactory.present(value);
        if ("deliveryOrProductionMethod".equals(key)) {
            valid = valid && value.isObject() && !value.path("method").asText("").isBlank();
        } else if ("expectedMonthlyThroughputOrSales".equals(key)) {
            valid = valid && value.isObject() && value.path("amount").isNumber()
                && value.path("amount").asDouble() >= 0 && !value.path("unit").asText("").isBlank();
        } else if ("technicalSupplyOperationalConstraints".equals(key)) {
            valid = valid && value.isArray() && !value.isEmpty();
            if (valid) for (JsonNode item : value) valid = valid && item.isTextual() && !item.asText().isBlank();
        }
        if (!valid) throw new IllegalStateException("TechOps proposal result is invalid");
    }

    private boolean sameAlternative(String key, JsonNode proposed, JsonNode previous) {
        if (new SnapshotHasher(mapper).hash(proposed).equals(new SnapshotHasher(mapper).hash(previous))) return true;
        if ("deliveryOrProductionMethod".equals(key)) {
            return normalized(proposed.path("method")).equals(normalized(previous.path("method")))
                && normalized(proposed.path("operatingModel")).equals(normalized(previous.path("operatingModel")))
                && normalized(proposed.path("partnerModel")).equals(normalized(previous.path("partnerModel")));
        }
        if ("expectedMonthlyThroughputOrSales".equals(key)) {
            return proposed.path("amount").decimalValue().compareTo(previous.path("amount").decimalValue()) == 0
                && normalized(proposed.path("unit")).equals(normalized(previous.path("unit")));
        }
        if ("technicalSupplyOperationalConstraints".equals(key)) {
            Set<String> proposedValues = normalizedSet(proposed);
            return !proposedValues.isEmpty() && proposedValues.equals(normalizedSet(previous));
        }
        return false;
    }

    private Set<String> normalizedSet(JsonNode values) {
        if (!values.isArray()) return Set.of();
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .map(this::normalized).collect(Collectors.toUnmodifiableSet());
    }

    private String normalized(JsonNode value) {
        return value.asText("").strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private TechOpsInputPreparation locked(TaskRunWorkerContext context) {
        return preparations.findLocked(context.subjectId(), context.projectId())
            .orElseThrow(() -> new IllegalStateException("TechOps preparation is missing"));
    }

    private JsonNode input(TaskRunWorkerContext context) {
        JsonNode input = mapper.readTree(context.inputSnapshot());
        if (!input.isObject()) throw new IllegalStateException("TechOps proposal input is invalid");
        return input;
    }

    private String safeError(String code, String reason) {
        if (STALE.equals(reason)) return STALE;
        if ("DEADLINE_EXCEEDED".equals(code) || "RATE_LIMITED".equals(code)) return code;
        return "AI_SERVICE_UNAVAILABLE";
    }

    public enum Outcome { SUCCEEDED, STALE }
}
