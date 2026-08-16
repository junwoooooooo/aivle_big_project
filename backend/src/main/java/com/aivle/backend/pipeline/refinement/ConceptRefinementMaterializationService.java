package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exact-round materialization; it never applies a proposal to product state. */
@Service
public class ConceptRefinementMaterializationService {
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementLineageGuard lineage;
    private final ConceptPortfolioJsonHasher hasher;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;

    public ConceptRefinementMaterializationService(ConceptRefinementRoundRepository rounds,
            ConceptRefinementLineageGuard lineage, ConceptPortfolioJsonHasher hasher,
            TaskRunService taskRuns, ObjectMapper mapper) {
        this.rounds = rounds; this.lineage = lineage; this.hasher = hasher;
        this.taskRuns = taskRuns; this.mapper = mapper;
    }

    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response, JsonNode result, JsonNode input,
            ConceptPortfolioSelection selection) {
        ConceptRefinementRound round = exact(context.taskRunId());
        JsonNode material = input.path("refinementMaterial");
        if (!inputBindingMatches(round, material.path("sourceBinding"))) throw new ContractViolation();
        if (!baselineBindingMatches(round, material.path("baselineBinding"))
                || !selectionMatches(round, context, input, selection) || !sourceCurrent(round, context)) {
            stale(claim, context, response, round, selection);
        }
        JsonNode proposals = result.path("refinementProposals");
        JsonNode rejected = result.path("driftRejections");
        require(proposals.isArray() && proposals.size() <= ConceptRefinementPolicy.MAX_PROPOSALS);
        require(rejected.isArray());
        validateProposals(proposals, material);
        round.materialize(mapper.writeValueAsString(proposals), mapper.writeValueAsString(rejected),
            !proposals.isEmpty());
        selection.completeAuxiliaryTask(context.taskRunId());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(result), context.inputHash(), response.resultSchemaVersion());
    }

    public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable, ConceptPortfolioSelection selection) {
        ConceptRefinementRound round = exact(context.taskRunId());
        if (sourceCurrent(round, context)) round.fail(code); else round.markStale();
        selection.clearAuxiliaryTaskIfActive(context.taskRunId());
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
    }

    private void stale(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response, ConceptRefinementRound round,
            ConceptPortfolioSelection selection) {
        round.markStale();
        selection.clearAuxiliaryTaskIfActive(context.taskRunId());
        taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), response.resultSchemaVersion(), "SOURCE_STALE");
        throw new StaleResult();
    }

    private boolean sourceCurrent(ConceptRefinementRound round, TaskRunWorkerContext context) {
        return lineage.proposalBaselineCurrent(context.ownerId(), context.projectId(), round);
    }

    private static boolean selectionMatches(ConceptRefinementRound round, TaskRunWorkerContext context,
            JsonNode input, ConceptPortfolioSelection selection) {
        return selection.isCurrent()
            && Objects.equals(selection.getId(), round.getSelectionId())
            && Objects.equals(selection.getProjectId(), context.projectId())
            && Objects.equals(selection.getActiveTaskRunId(), context.taskRunId())
            && input.path("expectedHypothesisRevision").isIntegralNumber()
            && input.path("expectedHypothesisRevision").asInt() == selection.getHypothesisRevision();
    }

    private static boolean inputBindingMatches(ConceptRefinementRound round, JsonNode binding) {
        return binding.isObject()
            && Objects.equals(binding.path("businessValidationSessionId").asText(null),
                round.getBusinessValidationSessionId())
            && binding.path("marketVersionId").asLong(Long.MIN_VALUE) == round.getSourceMarketVersionId()
            && binding.path("bmVersionId").asLong(Long.MIN_VALUE) == round.getSourceBmVersionId()
            && Objects.equals(binding.path("marketSeedSnapshotId").asText(null),
                round.getSourceMarketSeedSnapshotId())
            && binding.path("selectionId").asLong(Long.MIN_VALUE) == round.getSelectionId()
            && nullableInt(binding.get("selectionRevision"), round.getSourceSelectionRevision())
            && nullableInt(binding.get("bmPlanRevision"), round.getSourceBmPlanRevision());
    }

    private boolean baselineBindingMatches(ConceptRefinementRound round, JsonNode binding) {
        JsonNode overlay;
        try { overlay = mapper.readTree(round.baselineOverlayJson()); }
        catch (RuntimeException invalid) { return false; }
        return binding.isObject() && overlay != null && overlay.isObject()
            && binding.path("selectionRevision").asInt(-1) == round.baselineSelectionRevision()
            && binding.path("bmPlanRevision").asInt(-1) == round.baselineBmPlanRevision()
            && Objects.equals(binding.path("overlayHash").asText(null), hasher.hash(overlay));
    }

    private void validateProposals(JsonNode proposals, JsonNode material) {
        Set<String> evidence = new HashSet<>();
        material.path("marketEvidence").forEach(item -> evidence.add(item.path("id").asText()));
        JsonNode currentValues = material.path("currentEditableValues");
        require(currentValues.isObject());
        Set<String> legalRefs = new HashSet<>();
        material.path("allowedLegalRefs").forEach(value -> legalRefs.add(value.asText()));
        proposals.forEach(proposal -> {
            require(proposal.isObject());
            require(nonblank(proposal, "fieldKey") && proposal.has("currentValue")
                && proposal.hasNonNull("proposedValue"));
            String field = proposal.path("fieldKey").asText();
            require(currentValues.has(field));
            JsonNode authoritativeCurrent = currentValues.get(field);
            require(proposal.get("currentValue").equals(authoritativeCurrent));
            require(!proposal.get("proposedValue").equals(authoritativeCurrent));
            require(nonblank(proposal, "title") && nonblank(proposal, "beforeText")
                && nonblank(proposal, "afterText") && nonblank(proposal, "rationale"));
            String source = proposal.path("source").asText();
            if ("MARKET".equals(source)) {
                JsonNode ids = proposal.path("evidenceIds");
                require(ids.isArray() && !ids.isEmpty());
                ids.forEach(id -> require(evidence.contains(id.asText())));
            } else if ("LEGAL".equals(source)) {
                String ref = proposal.path("legalRef").asText("").strip();
                require(!ref.isBlank() && legalRefs.contains(ref));
            } else throw new ContractViolation();
        });
    }

    private ConceptRefinementRound exact(String taskRunId) {
        return rounds.findByTaskRunIdForUpdate(taskRunId).orElseThrow(ContractViolation::new);
    }

    private static boolean nullableInt(JsonNode node, Integer expected) {
        return expected == null ? node == null || node.isNull()
            : node != null && node.isIntegralNumber() && node.asInt() == expected;
    }
    private static boolean nonblank(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }
    private static void require(boolean condition) { if (!condition) throw new ContractViolation(); }

    public static final class ContractViolation extends RuntimeException { }
    public static final class StaleResult extends RuntimeException { }
}
