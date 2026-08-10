package com.aivle.backend.pipeline.selection.application;

import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver.Jurisdiction;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalStatus;
import com.aivle.backend.pipeline.selection.domain.ConceptHypothesisDecision;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.domain.HypothesisType;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ConceptSelectionActionCompletionService {
    private static final String STALE = "STALE_ACTION_RESULT";

    private final ConceptSelectionRepository selections;
    private final ConceptHypothesisDecisionRepository decisions;
    private final ConceptRepository concepts;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;
    private final LegalJurisdictionResolver jurisdictions;

    @Transactional
    public boolean start(TaskRunService.Claim claim, TaskRunWorkerContext context) {
        ConceptSelection selection = locked(context);
        JsonNode input = input(context);
        if (!fresh(selection, context, input)) {
            stale(claim, selection, context);
            return false;
        }
        selection.startAction(context.taskRunId());
        return true;
    }

    @Transactional
    public Outcome complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        ConceptSelection selection = locked(context);
        JsonNode input = input(context);
        if (!fresh(selection, context, input)) {
            stale(claim, selection, context);
            return Outcome.STALE;
        }
        ConceptHypothesisDecision current = latest(selection, input);
        if (context.taskType() == TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE) {
            completeAlternative(selection, current, context, input, response.result());
            taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
            return Outcome.SUCCEEDED;
        }
        if (context.taskType() != TaskType.CONCEPT_DELTA_LEGAL_REVIEW) {
            throw new IllegalStateException("unsupported concept selection action task");
        }
        Outcome outcome = completeDelta(selection, current, context, input, response.result());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        return outcome;
    }

    @Transactional
    public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        ConceptSelection selection = locked(context);
        if (context.taskRunId().equals(selection.getActiveActionTaskRunId())) {
            selection.completeAction(context.taskRunId(), "FAILED", safeError(code, reason));
        }
    }

    private void completeAlternative(ConceptSelection selection, ConceptHypothesisDecision current,
            TaskRunWorkerContext context, JsonNode input, JsonNode result) {
        HypothesisType type = type(input);
        if (!type.name().equals(result.path("hypothesisType").asText())
                || result.path("proposalVersion").asInt() != current.getProposalVersion() + 1) {
            throw new IllegalStateException("alternative proposal does not match command");
        }
        JsonNode proposed = result.path("proposedValue");
        validateValue(type, proposed);
        if (canonical(proposed).equals(canonical(mapper.readTree(current.getProposedValueJson())))) {
            throw new IllegalStateException("alternative proposal must differ from current value");
        }
        if (type == HypothesisType.TARGET_REGION
                && jurisdictions.resolve(proposed.asText()) != Jurisdiction.KR) {
            throw new IllegalStateException("alternative target region is unsupported");
        }
        current.reject();
        decisions.save(ConceptHypothesisDecision.alternative(
            current, mapper.writeValueAsString(proposed), context.ownerId()));
        selection.completeAction(context.taskRunId(), "SUCCEEDED", null);
    }

    private Outcome completeDelta(ConceptSelection selection, ConceptHypothesisDecision current,
            TaskRunWorkerContext context, JsonNode input, JsonNode result) {
        ConceptLegalStatus status;
        try {
            status = ConceptLegalStatus.valueOf(result.path("status").asText());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("delta legal status is invalid");
        }
        String safeResult = mapper.writeValueAsString(safeLegalResult(result));
        boolean eligible = status.isPubliclyEligible();
        JsonNode finalValue = input.path("requestedFinalValue");
        validateValue(type(input), finalValue);
        current.accept(mapper.writeValueAsString(finalValue), input.path("userEdited").asBoolean(),
            context.ownerId(), Instant.now(), true, eligible, safeResult);
        decisions.save(current);
        selection.completeAction(context.taskRunId(), eligible ? "SUCCEEDED" : "LEGAL_INELIGIBLE",
            eligible ? null : status.name());
        return eligible ? Outcome.SUCCEEDED : Outcome.LEGAL_INELIGIBLE;
    }

    private boolean fresh(ConceptSelection selection, TaskRunWorkerContext context, JsonNode input) {
        if (!selection.isCurrentSelection()
                || input.path("projectId").asLong() != context.projectId()
                || !selection.getConceptId().equals(input.path("conceptId").asText())
                || !selection.pendingMatches(context.taskRunId(), type(input),
                    input.path("currentDecisionId").asText(), input.path("expectedProposalVersion").asInt())) {
            return false;
        }
        Concept concept = concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(
            selection.getConceptId(), context.projectId()).orElse(null);
        if (concept == null || !concept.getCanonicalHash().equals(input.path("candidateHash").asText())) return false;
        ConceptHypothesisDecision latest = decisions
            .findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                selection.getId(), type(input)).orElse(null);
        return latest != null && latest.getId().equals(input.path("currentDecisionId").asText())
            && latest.getProposalVersion() == input.path("expectedProposalVersion").asInt();
    }

    private ConceptHypothesisDecision latest(ConceptSelection selection, JsonNode input) {
        return decisions.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                selection.getId(), type(input))
            .orElseThrow(() -> new IllegalStateException("pending hypothesis decision is missing"));
    }

    private void stale(TaskRunService.Claim claim, ConceptSelection selection, TaskRunWorkerContext context) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            "EXECUTION_FAILED", STALE, false);
        if (context.taskRunId().equals(selection.getActiveActionTaskRunId())) {
            selection.completeAction(context.taskRunId(), STALE, STALE);
        }
    }

    private ConceptSelection locked(TaskRunWorkerContext context) {
        Long selectionId;
        try {
            selectionId = Long.valueOf(context.subjectId());
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("selection subject id is invalid");
        }
        return selections.findByIdAndProjectIdAndDeletedAtIsNull(selectionId, context.projectId())
            .orElseThrow(() -> new IllegalStateException("selection action subject is missing"));
    }

    private JsonNode input(TaskRunWorkerContext context) {
        JsonNode input = mapper.readTree(context.inputSnapshot());
        if (!input.isObject()) throw new IllegalStateException("selection action input is invalid");
        return input;
    }

    private HypothesisType type(JsonNode input) {
        try {
            return HypothesisType.valueOf(input.path("hypothesisType").asText());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("hypothesis type is invalid");
        }
    }

    private void validateValue(HypothesisType type, JsonNode value) {
        boolean valid = value != null && !value.isMissingNode() && !value.isNull();
        if (type == HypothesisType.PRE_MARKET_SOM_SHARE) {
            valid = valid && value.isObject() && value.path("targetSharePercent").asDouble(0) > 0
                && value.path("horizonYears").asInt(0) > 0;
        } else if (type == HypothesisType.PRE_MARKET_SOM) {
            valid = valid && value.isObject() && value.path("amount").isNumber()
                && !value.path("currency").asText().isBlank();
        } else {
            valid = valid && value.isTextual() && !value.asText().isBlank();
        }
        if (!valid) throw new IllegalStateException("hypothesis value is invalid");
    }

    private ObjectNode safeLegalResult(JsonNode result) {
        if (!result.isObject()) throw new IllegalStateException("delta legal result is invalid");
        ObjectNode safe = (ObjectNode) result.deepCopy();
        if (safe.has("officialEvidence")) {
            var references = mapper.createArrayNode();
            for (JsonNode source : safe.path("officialEvidence")) {
                ObjectNode reference = references.addObject();
                for (String key : List.of("referenceIndex", "sourceType", "lawId", "officialIdentifier",
                        "lawName", "articleReference", "title", "officialSourceUri", "jurisdiction",
                        "promulgationDate", "effectiveDate", "retrievedAt", "contentHash", "registryVersion")) {
                    if (source.has(key) && !source.path(key).isNull()) {
                        reference.set(key, source.path(key).deepCopy());
                    }
                }
            }
            safe.set("officialEvidenceReferences", references);
        }
        safe.remove("officialEvidence");
        return safe;
    }

    private String canonical(JsonNode value) {
        return new SnapshotHasher(mapper).hash(value);
    }

    private String safeError(String code, String reason) {
        if (STALE.equals(reason)) return STALE;
        if ("DEADLINE_EXCEEDED".equals(code) || "RATE_LIMITED".equals(code)) return code;
        return "AI_SERVICE_UNAVAILABLE";
    }

    public enum Outcome { SUCCEEDED, LEGAL_INELIGIBLE, STALE }
}
