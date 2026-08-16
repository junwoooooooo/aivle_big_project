package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionMaterializationService.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Materializes only tagged CONFIRM_HYPOTHESES and DELTA_LEGAL executions. */
@Service
public class ConceptRefinementApplicationMaterializationService {
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final ConceptLegalRegulatoryReportRepository reports;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final BmPlanPreparationService bmPlans;
    private final ConceptPortfolioSelectionService selectionService;
    private final ConceptRefinementDecisionContract decisions;
    private final ConceptPortfolioJsonHasher hasher;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptRefinementApplicationMaterializationService(ConceptRefinementRoundRepository rounds,
            ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptPortfolioDeltaLegalReviewRepository deltas,
            ConceptLegalRegulatoryReportRepository reports,
            MarketAnalysisSeedSnapshotRepository seeds, BmPlanPreparationService bmPlans,
            ConceptPortfolioSelectionService selectionService,
            ConceptRefinementDecisionContract decisions, ConceptPortfolioJsonHasher hasher,
            TaskRunService taskRuns, ObjectMapper mapper, Clock clock) {
        this.rounds = rounds; this.selections = selections; this.hypotheses = hypotheses;
        this.deltas = deltas; this.reports = reports; this.seeds = seeds; this.bmPlans = bmPlans;
        this.selectionService = selectionService; this.decisions = decisions; this.hasher = hasher;
        this.taskRuns = taskRuns; this.mapper = mapper; this.clock = clock;
    }

    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response, JsonNode result, JsonNode input) {
        JsonNode binding = input.path("refinementApplication");
        ConceptRefinementRound round = lockedRound(binding);
        ConceptPortfolioSelection selection = lockedSelection(context);
        String action = input.path("action").asText();
        if ("CONFIRM_HYPOTHESES".equals(action)) {
            if (!confirmCurrent(round, selection, context, binding)) stale(claim, context, round, selection);
            Set<PortfolioHypothesisType> selectedTypes = selectedHypothesisTypes(round);
            if (!resultReady(result.path("hypotheses"), selectedTypes)) throw new ContractViolation();
            applyHypotheses(selection, result.path("hypotheses"), context.ownerId(), selectedTypes);
            staleDependents(selection.getId());
            ObjectNode bmPatch = (ObjectNode) decisions.applicationPlan(round).path("bmPlan");
            BmPlanPreparationService.PlanView patched = bmPlans.patchForRefinement(context.projectId(),
                context.ownerId(), round.getSourceBmPlanRevision(), bmPatch);
            boolean deltaRequired = latestRequired(selection.getId()).stream()
                .anyMatch(value -> value.isDeltaLegalRequired() && "PENDING".equals(value.getLegalReviewStatus()));
            selection.completeTask(context.taskRunId(), deltaRequired
                ? ConceptPortfolioSelectionStatus.DELTA_LEGAL_PENDING
                : ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT, true);
            round.recordAppliedLineage(selection.getHypothesisRevision(), patched.revision(), Instant.now(clock));
            adopt(claim, context, response);
            if (deltaRequired) {
                ObjectNode deltaBinding = binding(round, selection.getHypothesisRevision(), patched.revision());
                var deltaTask = selectionService.queueDeltaFromRefinement(context.ownerId(), selection,
                    round.getApplicationHash() + ":delta", deltaBinding);
                round.legalPending(deltaTask.getId());
            } else round.readyForFinalization();
            return;
        }
        if ("DELTA_LEGAL".equals(action)) {
            if (!deltaCurrent(round, selection, context, binding)) stale(claim, context, round, selection);
            JsonNode delta = result.path("deltaLegalResult");
            require(delta.isObject());
            boolean approved = delta.path("approved").asBoolean();
            deltas.save(ConceptPortfolioDeltaLegalReview.create(selection, context.taskRunId(),
                input.path("expectedHypothesisRevision").asInt(), delta.path("reviewToken").asText(),
                mapper.writeValueAsString(delta.path("hypothesisTypes")), delta.path("status").asText(),
                approved, mapper.writeValueAsString(result), hasher.hash(result)));
            if (approved) applyHypotheses(selection, result.path("hypotheses"), context.ownerId(),
                selectedHypothesisTypes(round));
            boolean allReady = approved && latestRequired(selection.getId()).stream()
                .allMatch(ConceptPortfolioHypothesisDecision::ready);
            selection.completeTask(context.taskRunId(), allReady
                ? ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT
                : ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED, false);
            adopt(claim, context, response);
            if (allReady) round.readyForFinalization(); else round.legalBlocked(context.taskRunId());
            return;
        }
        throw new ContractViolation();
    }

    public void fail(TaskRunService.Claim claim, TaskRunWorkerContext context,
            String code, String reason, boolean retryable, JsonNode input) {
        JsonNode binding = input.path("refinementApplication");
        ConceptRefinementRound round = lockedRound(binding);
        ConceptPortfolioSelection selection = lockedSelection(context);
        String action = input.path("action").asText();
        if ("CONFIRM_HYPOTHESES".equals(action)) {
            if (!confirmIdentity(round, context, binding)) stale(claim, context, round, selection);
            selection.clearAuxiliaryTaskIfActive(context.taskRunId());
            round.applicationFailed(context.taskRunId(), code);
        } else if ("DELTA_LEGAL".equals(action)) {
            if (!deltaIdentity(round, context, binding)) stale(claim, context, round, selection);
            selection.failTask(context.taskRunId(), ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED, code);
            round.legalFailed(context.taskRunId(), code);
        } else throw new ContractViolation();
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            code, reason, retryable);
    }

    private boolean confirmCurrent(ConceptRefinementRound round, ConceptPortfolioSelection selection,
            TaskRunWorkerContext context, JsonNode binding) {
        return confirmIdentity(round, context, binding) && selection.isCurrent()
            && Objects.equals(selection.getId(), round.getSelectionId())
            && selection.getHypothesisRevision() == round.getSourceSelectionRevision()
            && bmPlans.current(context.projectId()).revision() == round.getSourceBmPlanRevision()
            && exactSeedCurrent(round, context.projectId());
    }

    private boolean deltaCurrent(ConceptRefinementRound round, ConceptPortfolioSelection selection,
            TaskRunWorkerContext context, JsonNode binding) {
        return deltaIdentity(round, context, binding) && selection.isCurrent()
            && Objects.equals(selection.getId(), round.getSelectionId())
            && Objects.equals(selection.getHypothesisRevision(), round.getAppliedSelectionRevision())
            && bmPlans.current(context.projectId()).revision() == round.getAppliedBmPlanRevision();
    }

    private boolean confirmIdentity(ConceptRefinementRound round, TaskRunWorkerContext context, JsonNode binding) {
        return round.getState() == ConceptRefinementRound.State.APPLYING_HYPOTHESES
            && Objects.equals(round.getApplicationTaskRunId(), context.taskRunId())
            && bound(binding, round, round.getSourceSelectionRevision(), round.getSourceBmPlanRevision());
    }

    private boolean deltaIdentity(ConceptRefinementRound round, TaskRunWorkerContext context, JsonNode binding) {
        return round.getState() == ConceptRefinementRound.State.LEGAL_REVIEW_PENDING
            && Objects.equals(round.getDeltaLegalTaskRunId(), context.taskRunId())
            && bound(binding, round, round.getAppliedSelectionRevision(), round.getAppliedBmPlanRevision());
    }

    private boolean bound(JsonNode binding, ConceptRefinementRound round,
            Integer selectionRevision, Integer bmPlanRevision) {
        return binding.isObject()
            && binding.path("roundId").asLong() == round.getId()
            && Objects.equals(binding.path("decisionHash").asText(), round.getDecisionHash())
            && Objects.equals(binding.path("applicationHash").asText(), round.getApplicationHash())
            && binding.path("expectedSelectionRevision").asInt(-1) == selectionRevision
            && binding.path("expectedBmPlanRevision").asInt(-1) == bmPlanRevision
            && Objects.equals(binding.path("sourceMarketSeedSnapshotId").asText(),
                round.getSourceMarketSeedSnapshotId());
    }

    private ConceptRefinementRound lockedRound(JsonNode binding) {
        if (!binding.path("roundId").isIntegralNumber()) throw new ContractViolation();
        return rounds.findByIdForUpdate(binding.path("roundId").asLong()).orElseThrow(ContractViolation::new);
    }

    private ConceptPortfolioSelection lockedSelection(TaskRunWorkerContext context) {
        return selections.findLocked(Long.valueOf(context.subjectId()))
            .filter(value -> value.getProjectId().equals(context.projectId()))
            .orElseThrow(ContractViolation::new);
    }

    private boolean exactSeedCurrent(ConceptRefinementRound round, Long projectId) {
        return seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull(round.getSourceMarketSeedSnapshotId())
            .filter(value -> Objects.equals(value.getProjectId(), projectId)
                && Objects.equals(value.getPortfolioSelectionId(), round.getSelectionId()))
            .isPresent();
    }

    private void stale(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ConceptRefinementRound round, ConceptPortfolioSelection selection) {
        round.markStale();
        selection.clearAuxiliaryTaskIfActive(context.taskRunId());
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            "MODULE_INPUT_STALE", "LATE_OR_DUPLICATE_RESULT", false);
        throw new ConceptRefinementMaterializationService.StaleResult();
    }

    private void applyHypotheses(ConceptPortfolioSelection selection, JsonNode array, Long userId,
            Set<PortfolioHypothesisType> selectedTypes) {
        require(array.isArray() && array.size() == 7);
        Set<PortfolioHypothesisType> seen = EnumSet.noneOf(PortfolioHypothesisType.class);
        for (JsonNode item : array) {
            PortfolioHypothesisType type = PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
            if (!selectedTypes.contains(type)) continue;
            require(seen.add(type));
            ConceptPortfolioHypothesisDecision current = hypotheses
                .findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                    selection.getId(), type).orElseThrow(ContractViolation::new);
            current.apply(nullableJson(item.get("finalValue")), item.path("source").asText(),
                item.path("decisionStatus").asText(), item.path("locked").asBoolean(),
                item.path("semanticStatus").asText(), nullableText(item.path("semanticReason")),
                item.path("legalImpact").asText(), item.path("legalReviewStatus").asText(),
                item.path("deltaLegalRequired").asBoolean(), userId,
                item.path("finalValue").isNull() ? null : Instant.now(clock));
        }
        require(seen.equals(selectedTypes));
    }

    private boolean resultReady(JsonNode array, Set<PortfolioHypothesisType> selectedTypes) {
        if (!array.isArray() || array.size() != 7) return false;
        Set<PortfolioHypothesisType> seen = new HashSet<>();
        for (JsonNode item : array) {
            PortfolioHypothesisType type;
            try { type = PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText()); }
            catch (IllegalArgumentException invalid) { return false; }
            if (!selectedTypes.contains(type)) continue;
            if (!seen.add(type)) return false;
            if (!("ACCEPTED".equals(item.path("decisionStatus").asText())
                    || "USER_EDITED_ACCEPTED".equals(item.path("decisionStatus").asText()))
                    || item.path("finalValue").isNull()
                    || !"VALID".equals(item.path("semanticStatus").asText())) return false;
        }
        return seen.equals(selectedTypes);
    }

    private Set<PortfolioHypothesisType> selectedHypothesisTypes(ConceptRefinementRound round) {
        JsonNode values = decisions.applicationPlan(round).path("hypotheses");
        Set<PortfolioHypothesisType> selected = EnumSet.noneOf(PortfolioHypothesisType.class);
        try { for (String name : values.propertyNames()) selected.add(PortfolioHypothesisType.valueOf(name)); }
        catch (IllegalArgumentException invalid) { throw new ContractViolation(); }
        if (selected.isEmpty()) throw new ContractViolation();
        return selected;
    }

    private java.util.List<ConceptPortfolioHypothesisDecision> latestRequired(Long selectionId) {
        java.util.List<ConceptPortfolioHypothesisDecision> values = hypotheses
            .findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(selectionId).stream()
            .collect(java.util.stream.Collectors.toMap(ConceptPortfolioHypothesisDecision::getHypothesisType,
                value -> value, (first, ignored) -> first, LinkedHashMap::new)).values().stream().toList();
        if (values.size() != 7) throw new ContractViolation();
        return values;
    }

    private void staleDependents(Long selectionId) {
        reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(selectionId, "CURRENT")
            .forEach(ConceptLegalRegulatoryReport::markStale);
        seeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selectionId)
            .forEach(value -> value.markStale(Instant.now(clock)));
    }

    private ObjectNode binding(ConceptRefinementRound round, int selectionRevision, int bmRevision) {
        ObjectNode binding = mapper.createObjectNode();
        binding.put("roundId", round.getId()); binding.put("decisionHash", round.getDecisionHash());
        binding.put("applicationHash", round.getApplicationHash());
        binding.put("expectedSelectionRevision", selectionRevision);
        binding.put("expectedBmPlanRevision", bmRevision);
        binding.put("sourceMarketSeedSnapshotId", round.getSourceMarketSeedSnapshotId());
        return binding;
    }

    private void adopt(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(response.result()), context.inputHash(), response.resultSchemaVersion());
    }

    private String nullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : mapper.writeValueAsString(value);
    }
    private String nullableText(JsonNode value) {
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
    private static void require(boolean condition) { if (!condition) throw new ContractViolation(); }
}
