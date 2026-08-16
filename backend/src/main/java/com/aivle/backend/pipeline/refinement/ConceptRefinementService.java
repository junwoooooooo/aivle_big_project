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

    public ConceptRefinementService(ProjectRepository projects, BusinessValidationCoordinator validations,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository marketSeeds,
            ConceptRefinementRoundRepository rounds, ConceptRefinementMaterialFactory materials,
            ConceptPortfolioSelectionTaskFactory tasks, CanonicalInputHasher inputHasher,
            ObjectMapper mapper, ConceptRefinementDecisionContract decisions,
            ConceptRefinementLineageGuard lineage) {
        this.projects = projects; this.validations = validations; this.selections = selections;
        this.marketSeeds = marketSeeds; this.rounds = rounds; this.materials = materials;
        this.tasks = tasks; this.inputHasher = inputHasher; this.mapper = mapper;
        this.decisions = decisions;
        this.lineage = lineage;
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

    @Transactional
    public CurrentView retry(Long ownerId, Long projectId, String commandKey, String correlationId) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(commandKey);
        ConceptRefinementRound round = rounds
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (key.equals(round.getCommandIdempotencyKey())) {
            CompletedSource replaySource;
            try { replaySource = validations.requireCurrentCompletedSource(ownerId, projectId); }
            catch (BusinessException unavailable) {
                round.markStale();
                return view(round, true);
            }
            if (!round.boundTo(replaySource)) {
                round.markStale();
                return view(round, true);
            }
            return replay(projectId, replaySource,
                requireLineage(projectId, replaySource), round);
        }
        CompletedSource source;
        try { source = validations.requireCurrentCompletedSource(ownerId, projectId); }
        catch (BusinessException unavailable) {
            round.markStale();
            return view(round, true);
        }
        if (!round.boundTo(source)) {
            round.markStale();
            return view(round, true);
        }
        if (round.getState() != ConceptRefinementRound.State.FAILED
                || round.getAttempt() >= ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "다듬기 제안 재시도를 사용할 수 없습니다.");
        }
        ConceptPortfolioSelection selection = requireLineage(projectId, source);
        ObjectNode input = materials.input(projectId, selection, source, round.getAttempt() + 1);
        TaskRun task = tasks.create(ownerId, selection, "REFINE_FROM_MARKET", input, key, correlationId);
        round.retry(task.getId(), key, task.getInputHash());
        return view(round, false);
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
            : !lineage.preApplyCurrent(ownerId, projectId, round);
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
        return new CurrentView(round.getBusinessValidationSessionId(), state, stale, round.getRoundNumber(), policy(),
            proposalSet == null ? jsonArray(null) : proposalSet.projected(),
            jsonArray(round.getDriftRejectionsJson()),
            round.getLastErrorCode(), new RetryView(retryAvailable, round.getAttempt(),
                ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND),
            proposalSet == null ? null : proposalSet.hash(), decisions.decisionView(round));
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
    public record CurrentView(String sourceBusinessValidationSessionId, String state, boolean stale, int round, PolicyView policy,
                              JsonNode proposals, JsonNode rejected, String errorCode,
                              RetryView retry, String proposalSetHash,
                              ConceptRefinementDecisionContract.DecisionView decision) {
        static CurrentView notStarted() {
            return new CurrentView(null, "NOT_STARTED", false, 0,
                new PolicyView(ConceptRefinementPolicy.VERSION,
                    ConceptRefinementPolicy.MAX_ROUNDS, ConceptRefinementPolicy.MAX_PROPOSALS,
                    (int) (ConceptRefinementPolicy.PRICE_TOLERANCE * 100),
                    ConceptRefinementPolicy.LIST_CHANGE_ALLOWANCE),
                JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.arrayNode(),
                null, new RetryView(false, 0, ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND),
                null, null);
        }
    }
}
