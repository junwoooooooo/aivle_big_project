package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
public class ConceptRefinementService {
    private final ProjectRepository projects;
    private final BusinessValidationCoordinator validations;
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementMaterialFactory materials;
    private final ConceptPortfolioSelectionTaskFactory tasks;
    private final CanonicalInputHasher inputHasher;
    private final ObjectMapper mapper;
    private final ConceptRefinementDecisionContract decisions;
    private final ConceptRefinementLineageGuard lineage;
    private final ConceptRefinementApplicationBeforeContract applicationBefore;

    public ConceptRefinementService(ProjectRepository projects, BusinessValidationCoordinator validations,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository marketSeeds,
            ConceptRefinementRoundRepository rounds, ConceptRefinementMaterialFactory materials,
            ConceptPortfolioSelectionTaskFactory tasks, CanonicalInputHasher inputHasher,
            ObjectMapper mapper, ConceptRefinementDecisionContract decisions,
            ConceptRefinementLineageGuard lineage,
            ConceptRefinementApplicationBeforeContract applicationBefore) {
        this.projects = projects; this.validations = validations; this.selections = selections;
        this.marketSeeds = marketSeeds; this.rounds = rounds; this.materials = materials;
        this.tasks = tasks; this.inputHasher = inputHasher; this.mapper = mapper;
        this.decisions = decisions;
        this.lineage = lineage;
        this.applicationBefore = applicationBefore;
    }

    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String commandKey, String correlationId) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(commandKey);
        CompletedSource source = validations.requireCurrentCompletedSource(ownerId, projectId);
        ConceptPortfolioSelection selection = requireLineage(projectId, source);
        ConceptRefinementRound existing = rounds
            .findTopByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberDescIdDesc(
                projectId, source.businessValidationSessionId()).orElse(null);
        if (existing != null) {
            if (key.equals(existing.getCommandIdempotencyKey()))
                return replay(projectId, source, selection, existing);
            if (Set.of(ConceptRefinementRound.State.PROPOSING,
                    ConceptRefinementRound.State.AWAITING_DECISION,
                    ConceptRefinementRound.State.NO_CHANGES).contains(existing.getState())) {
                return view(existing, false);
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "실패한 다듬기 제안은 retry 명령으로만 다시 실행할 수 있습니다.");
        }
        rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .filter(value -> !value.boundTo(source)).ifPresent(ConceptRefinementRound::markStale);
        ObjectNode input = materials.input(projectId, selection, source, 1);
        TaskRun task = tasks.create(ownerId, selection, "REFINE_FROM_MARKET", input, key, correlationId);
        ConceptRefinementRound round = rounds.save(ConceptRefinementRound.start(projectId, source,
            task.getId(), key, task.getInputHash()));
        return view(round, false);
    }

    /**
     * Compatibility bridge called by the frozen MAIN market worker after BM adoption.
     * The FULL session remains lineage only: execution authority stays with that worker and this
     * method delegates to the existing v3 command using one key per completed validation session.
     */
    @Transactional
    public Optional<TaskRun> startFirstRoundAfterResearch(Long projectId) {
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection == null) return Optional.empty();
        CompletedSource source = validations.requireCurrentCompletedSource(
            selection.getSelectedByUserId(), projectId);
        String key = "auto-refinement-" + source.businessValidationSessionId();
        start(selection.getSelectedByUserId(), projectId, key, key);
        return Optional.empty();
    }

    @Transactional
    public CurrentView retry(Long ownerId, Long projectId, String commandKey, String correlationId) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(commandKey);
        ConceptRefinementRound round = rounds
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (key.equals(round.getCommandIdempotencyKey())) {
            if (!lineage.proposalBaselineCurrent(ownerId, projectId, round)) {
                round.markStale();
                return view(round, true);
            }
            return replayRound(projectId, requireBaselineSelection(projectId, round), round);
        }
        if (!lineage.proposalBaselineCurrent(ownerId, projectId, round)) {
            round.markStale();
            return view(round, true);
        }
        if (round.getState() != ConceptRefinementRound.State.FAILED
                || round.getAttempt() >= ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "다듬기 제안 재시도를 사용할 수 없습니다.");
        }
        ConceptPortfolioSelection selection = requireBaselineSelection(projectId, round);
        ObjectNode input = materials.inputForRound(projectId, selection, round, round.getAttempt() + 1,
            history(round));
        TaskRun task = tasks.create(ownerId, selection, "REFINE_FROM_MARKET", input, key, correlationId);
        round.retry(task.getId(), key, task.getInputHash());
        return view(round, false);
    }

    @Transactional
    public CurrentView next(Long ownerId, Long projectId, String commandKey, String correlationId,
            Integer expectedRound, String expectedProposalSetHash, String expectedDecisionHash) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(commandKey);
        if (expectedRound == null || expectedRound < 1 || expectedRound > ConceptRefinementPolicy.MAX_ROUNDS)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "expectedRound가 올바르지 않습니다.");
        ConceptRefinementRound latest = rounds
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ConceptRefinementRound parent;
        ConceptRefinementRound child = null;
        if (latest.getRoundNumber() == expectedRound) parent = latest;
        else if (latest.getRoundNumber() == expectedRound + 1 && latest.getParentRoundId() != null) {
            child = latest;
            parent = rounds.findByIdForUpdate(latest.getParentRoundId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        } else {
            List<ConceptRefinementRound> cycle = rounds
                .findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(
                    projectId, latest.getBusinessValidationSessionId());
            parent = cycle.stream().filter(value -> value.getRoundNumber() == expectedRound)
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                    "현재 refinement round와 일치하지 않습니다."));
            child = rounds.findByParentRoundIdAndDeletedAtIsNull(parent.getId()).orElse(null);
            if (child == null) throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "현재 refinement round와 일치하지 않습니다.");
        }
        validateNextTokens(parent, expectedProposalSetHash, expectedDecisionHash);
        if (child != null) {
            if (key.equals(child.getCommandIdempotencyKey()))
                return replayRound(projectId, requireBaselineSelection(projectId, child), child);
            return view(child, !lineage.proposalBaselineCurrent(ownerId, projectId, child));
        }
        if (parent.getRoundNumber() >= ConceptRefinementPolicy.MAX_ROUNDS)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "최대 refinement round에 도달했습니다.");
        boolean declining = parent.getState() == ConceptRefinementRound.State.AWAITING_DECISION;
        boolean continuing = parent.getState() == ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION;
        boolean recovered = parent.getState() == ConceptRefinementRound.State.RECOVERED;
        if (!declining && !continuing && !recovered)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "현재 상태에서는 다음 제안을 요청할 수 없습니다.");
        boolean current = declining
            ? lineage.proposalBaselineCurrent(ownerId, projectId, parent)
            : lineage.postApplyCurrent(projectId, parent);
        if (!current) {
            parent.markStale();
            return view(parent, true);
        }
        int selectionRevision = declining ? parent.baselineSelectionRevision() : parent.getAppliedSelectionRevision();
        int bmRevision = declining ? parent.baselineBmPlanRevision() : parent.getAppliedBmPlanRevision();
        ObjectNode overlay = overlay(parent.baselineOverlayJson());
        boolean seedRequired = parent.isSeedRebuildRequired();
        if (continuing) {
            ObjectNode plan = decisions.applicationPlan(parent);
            mergeOverlay(overlay, plan.path("overlay"));
            seedRequired = seedRequired || !plan.path("hypotheses").isEmpty() || !plan.path("overlay").isEmpty();
            parent.continued();
        } else if (recovered) {
            seedRequired = true;
            parent.continued();
        } else parent.declined();
        ConceptRefinementRound draft = ConceptRefinementRound.next(parent, selectionRevision, bmRevision,
            mapper.writeValueAsString(overlay), seedRequired, "pending", key, "sha256:" + "0".repeat(64));
        ConceptPortfolioSelection selection = requireBaselineSelection(projectId, draft);
        List<ConceptRefinementRound> history = history(draft);
        ObjectNode input = materials.inputForRound(projectId, selection, draft, 1, history);
        TaskRun task = tasks.create(ownerId, selection, "REFINE_FROM_MARKET", input, key, correlationId);
        ConceptRefinementRound saved = rounds.save(ConceptRefinementRound.next(parent, selectionRevision, bmRevision,
            mapper.writeValueAsString(overlay), seedRequired, task.getId(), key, task.getInputHash()));
        return view(saved, false);
    }

    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        return rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .map(round -> currentView(ownerId, projectId, round))
            .orElseGet(CurrentView::notStarted);
    }

    private CurrentView currentView(Long ownerId, Long projectId, ConceptRefinementRound round) {
        boolean stale = round.postApplyState()
            ? !lineage.postApplyCurrent(projectId, round)
            : !lineage.proposalBaselineCurrent(ownerId, projectId, round);
        if (stale && round.getState() != ConceptRefinementRound.State.STALE) round.markStale();
        return view(round, stale);
    }

    private CurrentView replay(Long projectId, CompletedSource source,
            ConceptPortfolioSelection selection, ConceptRefinementRound round) {
        ObjectNode input = materials.input(projectId, selection, source, round.getAttempt());
        String hash = inputHasher.hash(ConceptPortfolioSelectionTaskFactory.TYPE,
            "1.0", "ko-KR", mapper.writeValueAsString(input));
        if (!Objects.equals(hash, round.getCanonicalMaterialHash()))
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return view(round, false);
    }

    private CurrentView replayRound(Long projectId, ConceptPortfolioSelection selection,
            ConceptRefinementRound round) {
        ObjectNode input = materials.inputForRound(projectId, selection, round, round.getAttempt(), history(round));
        String hash = inputHasher.hash(ConceptPortfolioSelectionTaskFactory.TYPE,
            "1.0", "ko-KR", mapper.writeValueAsString(input));
        if (!Objects.equals(hash, round.getCanonicalMaterialHash()))
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return view(round, false);
    }

    private List<ConceptRefinementRound> history(ConceptRefinementRound round) {
        return rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(
            round.getProjectId(), round.getBusinessValidationSessionId()).stream()
            .filter(value -> value.getRoundNumber() < round.getRoundNumber())
            .toList();
    }

    private ConceptPortfolioSelection requireBaselineSelection(Long projectId, ConceptRefinementRound round) {
        return selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .filter(value -> Objects.equals(value.getId(), round.getSelectionId())
                && value.getHypothesisRevision() == round.baselineSelectionRevision())
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
    }

    private void validateNextTokens(ConceptRefinementRound parent,
            String expectedProposalSetHash, String expectedDecisionHash) {
        boolean declined = Set.of(ConceptRefinementRound.State.AWAITING_DECISION,
            ConceptRefinementRound.State.DECLINED).contains(parent.getState());
        boolean continued = Set.of(ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION,
            ConceptRefinementRound.State.CONTINUED, ConceptRefinementRound.State.RECOVERED).contains(parent.getState());
        if (declined) {
            String actual = decisions.proposalSet(parent).hash();
            if (expectedDecisionHash != null || !Objects.equals(actual, expectedProposalSetHash))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        } else if (continued) {
            if (expectedProposalSetHash != null || !Objects.equals(parent.getDecisionHash(), expectedDecisionHash))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private ObjectNode overlay(String json) {
        JsonNode value = mapper.readTree(json);
        if (value == null || !value.isObject()) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        return (ObjectNode) value.deepCopy();
    }

    private void mergeOverlay(ObjectNode target, JsonNode addition) {
        if (!addition.isObject()) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        addition.propertyNames().forEach(field -> {
            if (!Set.of("targetUsers", "featureSet").contains(field))
                throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
            target.set(field, addition.get(field).deepCopy());
        });
    }

    private ConceptPortfolioSelection requireLineage(Long projectId, CompletedSource source) {
        if (source.selectionRevision() == null || source.bmPlanRevision() == null)
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "입력 revision이 고정된 사업 검증을 다시 실행해 주세요.");
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .filter(value -> value.getId().equals(source.selectionId())
                && Objects.equals(value.getHypothesisRevision(), source.selectionRevision()))
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value -> value.getId().equals(source.marketSeedSnapshotId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        return selection;
    }

    CurrentView view(ConceptRefinementRound round, boolean computedStale) {
        boolean stale = computedStale || round.getState() == ConceptRefinementRound.State.STALE;
        String state = stale ? "STALE" : round.getState().name();
        boolean retryAvailable = !stale && round.getState() == ConceptRefinementRound.State.FAILED
            && round.getAttempt() < ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND;
        ConceptRefinementDecisionContract.ProposalSet proposalSet = round.getProposalJson() == null
            ? null : decisions.proposalSet(round);
        boolean nextAvailable = !stale && round.getRoundNumber() < ConceptRefinementPolicy.MAX_ROUNDS
            && Set.of(ConceptRefinementRound.State.AWAITING_DECISION,
                ConceptRefinementRound.State.APPLIED_PENDING_FINALIZATION,
                ConceptRefinementRound.State.RECOVERED).contains(round.getState());
        boolean legalRecoveryAvailable = !stale && round.getState() == ConceptRefinementRound.State.LEGAL_BLOCKED
            && applicationBefore.available(round);
        String nextReason = nextAvailable ? null
            : round.getRoundNumber() >= ConceptRefinementPolicy.MAX_ROUNDS ? "MAX_ROUNDS" : "STATE_UNAVAILABLE";
        return new CurrentView(round.getBusinessValidationSessionId(), state, stale, round.getRoundNumber(), policy(),
            proposalSet == null ? jsonArray(null) : proposalSet.projected(),
            jsonArray(round.getDriftRejectionsJson()),
            round.getLastErrorCode(), new RetryView(retryAvailable, round.getAttempt(),
                ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND),
            proposalSet == null ? null : proposalSet.hash(), decisions.decisionView(round),
            new NextRoundView(nextAvailable, round.getRoundNumber(), ConceptRefinementPolicy.MAX_ROUNDS, nextReason),
            new LegalRecoveryView(legalRecoveryAvailable));
    }

    private PolicyView policy() {
        return new PolicyView(ConceptRefinementPolicy.VERSION, ConceptRefinementPolicy.MAX_ROUNDS,
            ConceptRefinementPolicy.MAX_PROPOSALS,
            (int) (ConceptRefinementPolicy.PRICE_TOLERANCE * 100),
            ConceptRefinementPolicy.LIST_CHANGE_ALLOWANCE);
    }

    private JsonNode jsonArray(String value) {
        if (value == null) return mapper.createArrayNode();
        JsonNode parsed = mapper.readTree(value);
        return parsed.isArray() ? parsed : mapper.createArrayNode();
    }

    private void owned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void ownedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private static String validKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }

    public record PolicyView(String version, int maxRounds, int maxProposals,
                             int priceChangePercent, int listChangeAllowance) { }
    public record RetryView(boolean available, int attempts, int maxAttempts) { }
    public record NextRoundView(boolean available, int currentRound, int maxRounds, String reason) { }
    public record LegalRecoveryView(boolean available) { }
    public record CurrentView(String sourceBusinessValidationSessionId, String state, boolean stale, int round, PolicyView policy,
                              JsonNode proposals, JsonNode rejected, String errorCode,
                              RetryView retry, String proposalSetHash,
                              ConceptRefinementDecisionContract.DecisionView decision,
                              NextRoundView nextRound, LegalRecoveryView recovery) {
        static CurrentView notStarted() {
            return new CurrentView(null, "NOT_STARTED", false, 0,
                new PolicyView(ConceptRefinementPolicy.VERSION,
                    ConceptRefinementPolicy.MAX_ROUNDS, ConceptRefinementPolicy.MAX_PROPOSALS,
                    (int) (ConceptRefinementPolicy.PRICE_TOLERANCE * 100),
                    ConceptRefinementPolicy.LIST_CHANGE_ALLOWANCE),
                JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.arrayNode(),
                null, new RetryView(false, 0, ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND),
                null, null, new NextRoundView(false, 0, ConceptRefinementPolicy.MAX_ROUNDS, "NOT_STARTED"),
                new LegalRecoveryView(false));
        }
    }
}
