package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Applies only the immutable plan stored by B2A. */
@Service
public class ConceptRefinementApplicationService {
    private final ProjectRepository projects;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementDecisionContract contract;
    private final ConceptRefinementLineageGuard lineage;
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioSelectionService selectionService;
    private final BmPlanPreparationService bmPlans;
    private final MarketAnalysisSeedSnapshotRepository seeds;
    private final ConceptRefinementService refinement;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptRefinementApplicationService(ProjectRepository projects,
            ConceptRefinementRoundRepository rounds, ConceptRefinementDecisionContract contract,
            ConceptRefinementLineageGuard lineage, ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioSelectionService selectionService, BmPlanPreparationService bmPlans,
            MarketAnalysisSeedSnapshotRepository seeds, ConceptRefinementService refinement,
            ObjectMapper mapper, Clock clock) {
        this.projects = projects; this.rounds = rounds; this.contract = contract;
        this.lineage = lineage; this.selections = selections; this.selectionService = selectionService;
        this.bmPlans = bmPlans; this.seeds = seeds; this.refinement = refinement;
        this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public ConceptRefinementService.CurrentView apply(Long ownerId, Long projectId,
            String idempotencyKey, Integer expectedRound, String expectedDecisionHash) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(idempotencyKey);
        ConceptRefinementRound round = currentRound(projectId);
        validateExpected(round, expectedRound, expectedDecisionHash, key);
        String applicationHash = contract.applicationHash(round);

        if (round.getApplicationIdempotencyKey() != null) {
            if (key.equals(round.getApplicationIdempotencyKey())) {
                if (!applicationHash.equals(round.getApplicationHash()))
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                return guardedView(ownerId, projectId, round);
            }
            if (round.getState() != ConceptRefinementRound.State.APPLY_FAILED
                    || round.getApplicationAttempt() == null || round.getApplicationAttempt() >= 3)
                throw unavailable();
        } else if (round.getState() != ConceptRefinementRound.State.DECISION_RECORDED) {
            throw unavailable();
        }

        if (!lineage.proposalBaselineCurrent(ownerId, projectId, round)) {
            round.markStale();
            return refinement.view(round, true);
        }
        ObjectNode plan = contract.applicationPlan(round);
        ObjectNode hypotheses = object(plan, "hypotheses");
        ObjectNode bmPatch = object(plan, "bmPlan");
        ObjectNode overlay = object(plan, "overlay");
        validateOverlay(overlay);
        ConceptPortfolioSelection selection = selections.findLocked(round.getSelectionId())
            .filter(value -> value.isCurrent()
                && value.getHypothesisRevision() == round.baselineSelectionRevision())
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));

        if (!hypotheses.isEmpty()) {
            ObjectNode binding = binding(round, applicationHash,
                round.baselineSelectionRevision(), round.baselineBmPlanRevision());
            TaskRun task = selectionService.confirmFromRefinement(ownerId, projectId,
                round.getSelectionId(), hypotheses, binding, key);
            if (round.getState() == ConceptRefinementRound.State.APPLY_FAILED)
                round.retryApplication(key, applicationHash, task.getId(), Instant.now(clock));
            else round.startApplication(key, applicationHash, task.getId(), Instant.now(clock));
            return refinement.view(round, false);
        }

        round.startLocalApplication(key, applicationHash, Instant.now(clock));
        BmPlanPreparationService.PlanView patched = bmPlans.patchForRefinement(projectId, ownerId,
            round.baselineBmPlanRevision(), bmPatch);
        if (!overlay.isEmpty()) exactSeed(round, projectId).markStale(Instant.now(clock));
        round.recordAppliedLineage(selection.getHypothesisRevision(), patched.revision(), Instant.now(clock));
        round.readyForFinalization();
        return refinement.view(round, false);
    }

    @Transactional
    public ConceptRefinementService.CurrentView retryLegal(Long ownerId, Long projectId,
            String idempotencyKey, Integer expectedRound, String expectedDecisionHash) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(idempotencyKey);
        ConceptRefinementRound round = currentRound(projectId);
        validateExpected(round, expectedRound, expectedDecisionHash, key);
        if (round.getState() == ConceptRefinementRound.State.LEGAL_REVIEW_PENDING)
            return guardedView(ownerId, projectId, round);
        if (round.getState() != ConceptRefinementRound.State.LEGAL_REVIEW_FAILED) throw unavailable();
        if (!lineage.postApplyCurrent(projectId, round)) {
            round.markStale();
            return refinement.view(round, true);
        }
        ConceptPortfolioSelection selection = selections.findLocked(round.getSelectionId())
            .filter(value -> value.isCurrent()
                && Objects.equals(value.getHypothesisRevision(), round.getAppliedSelectionRevision()))
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        ObjectNode binding = binding(round, round.getApplicationHash(),
            round.getAppliedSelectionRevision(), round.getAppliedBmPlanRevision());
        TaskRun task = selectionService.queueDeltaFromRefinement(ownerId, selection, key, binding);
        round.retryLegal(task.getId());
        return refinement.view(round, false);
    }

    ObjectNode binding(ConceptRefinementRound round, String applicationHash,
            int selectionRevision, int bmPlanRevision) {
        if (round.getId() == null) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        ObjectNode binding = mapper.createObjectNode();
        binding.put("roundId", round.getId());
        binding.put("decisionHash", round.getDecisionHash());
        binding.put("applicationHash", applicationHash);
        binding.put("expectedSelectionRevision", selectionRevision);
        binding.put("expectedBmPlanRevision", bmPlanRevision);
        binding.put("sourceMarketSeedSnapshotId", round.getSourceMarketSeedSnapshotId());
        return binding;
    }

    private ConceptRefinementService.CurrentView guardedView(Long ownerId, Long projectId,
            ConceptRefinementRound round) {
        boolean current = round.postApplyState()
            ? lineage.postApplyCurrent(projectId, round)
            : lineage.proposalBaselineCurrent(ownerId, projectId, round);
        if (!current) round.markStale();
        return refinement.view(round, !current);
    }

    private ConceptRefinementRound currentRound(Long projectId) {
        return rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateExpected(ConceptRefinementRound round, Integer expectedRound,
            String expectedDecisionHash, String key) {
        if (expectedRound == null || expectedRound != round.getRoundNumber())
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        if (expectedDecisionHash == null || !expectedDecisionHash.matches("sha256:[0-9a-f]{64}"))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        if (!Objects.equals(expectedDecisionHash, round.getDecisionHash())) {
            if (key.equals(round.getApplicationIdempotencyKey()))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
    }

    private ObjectNode object(ObjectNode parent, String name) {
        JsonNode value = parent.path(name);
        if (!value.isObject()) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        return (ObjectNode) value;
    }

    private void validateOverlay(ObjectNode overlay) {
        for (String key : overlay.propertyNames()) {
            if (!java.util.Set.of("targetUsers", "featureSet").contains(key))
                throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
    }

    private MarketAnalysisSeedSnapshot exactSeed(ConceptRefinementRound round, Long projectId) {
        return seeds.findByIdAndDeletedAtIsNull(round.getSourceMarketSeedSnapshotId())
            .filter(value -> Objects.equals(value.getProjectId(), projectId)
                && Objects.equals(value.getPortfolioSelectionId(), round.getSelectionId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
    }

    private void ownedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private static String validKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INVALID_REQUEST,
            "현재 refinement 상태에서는 application을 시작할 수 없습니다.");
    }
}
