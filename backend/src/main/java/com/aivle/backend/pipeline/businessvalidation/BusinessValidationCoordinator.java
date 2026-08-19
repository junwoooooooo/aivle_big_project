package com.aivle.backend.pipeline.businessvalidation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exact-lineage projection for the worker-owned Market -> BM -> refinement chain.
 *
 * <p>This service never creates automatic downstream work. The only automatic execution authority
 * is {@code MarketResearchWorker}; this projection records the exact versions and pinned plan that
 * worker used. The scheduled reconciler only repairs missed projection writes.
 */
@Service
@RequiredArgsConstructor
public class BusinessValidationCoordinator {

    private static final List<BusinessValidationSession.State> ACTIVE_STATES = List.of(
        BusinessValidationSession.State.MARKET_RUNNING,
        BusinessValidationSession.State.MARKET_COMPLETED,
        BusinessValidationSession.State.BM_RUNNING);

    private final ProjectRepository projects;
    private final BusinessValidationSessionRepository sessions;
    private final MarketResearchRunRepository marketRuns;
    private final MarketResearchService market;
    private final BmPlanPreparationService bmPlans;

    /** Compatibility command. It starts only Market; the worker owns all automatic continuation. */
    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String asOf,
            String commandKey, String correlationId) {
        Project project = ownedForUpdate(ownerId, projectId);
        String normalizedCommandKey = validCommandKey(commandKey);
        BusinessValidationSession replay = sessions
            .findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(projectId, normalizedCommandKey)
            .orElse(null);
        if (replay != null) return view(ownerId, replay);
        BusinessValidationSession active = sessions
            .findTopByProjectIdAndStateInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, ACTIVE_STATES).orElse(null);
        if (active != null) {
            if (stale(ownerId, active)) active.markStale();
            else return view(ownerId, active);
        }
        MarketResearchService.RunView started = market.startFull(ownerId, projectId, asOf,
            derivedKey("market", normalizedCommandKey), correlationId);
        return view(ownerId, createProjection(project, started, normalizedCommandKey));
    }

    /** Called by the canonical /market-research controller after its direct service start. */
    @Transactional
    public void observeMarketStarted(Long ownerId, Long projectId,
            MarketResearchService.RunView started, String commandKey) {
        Project project = ownedForUpdate(ownerId, projectId);
        if (sessions.findByMarketTaskRunIdAndDeletedAtIsNull(started.taskRunId()).isPresent()) return;
        String projectionKey = commandKey == null || commandKey.isBlank()
            ? "market-projection-" + started.taskRunId() : commandKey.strip();
        createProjection(project, started, validCommandKey(projectionKey));
    }

    private BusinessValidationSession createProjection(Project project,
            MarketResearchService.RunView started, String commandKey) {
        BusinessValidationSession replay = sessions
            .findByMarketTaskRunIdAndDeletedAtIsNull(started.taskRunId()).orElse(null);
        if (replay != null) return replay;
        int sourceBmPlanRevision = bmPlans.current(project.getId()).revision();
        return sessions.save(BusinessValidationSession.start(project,
            exactRun(project.getId(), started.taskRunId()), sourceBmPlanRevision, commandKey));
    }

    @Transactional(readOnly = true)
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        return sessions.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .map(value -> view(ownerId, value)).orElseGet(CurrentView::notStarted);
    }

    /** Internal authority for downstream reads; it never schedules downstream work. */
    @Transactional(readOnly = true)
    public CompletedSource requireCurrentCompletedSource(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        BusinessValidationSession session = sessions
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                "현재 완료된 사업 검증이 필요합니다."));
        if (session.getState() != BusinessValidationSession.State.COMPLETED
                || session.getMarketVersionId() == null || session.getBmVersionId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "현재 완료된 사업 검증이 필요합니다.");
        }
        if (stale(ownerId, session)) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "사업 검증 입력이 변경되었습니다. 사업 검증을 다시 실행해 주세요.");
        }
        return completedSource(session);
    }

    /** Project a committed FULL version and return the exact input for the worker-owned BM start. */
    @Transactional
    public Optional<MarketChainSource> marketCompleted(String marketTaskRunId) {
        BusinessValidationSession session = sessions.findByMarketTaskRunIdForUpdate(marketTaskRunId)
            .orElse(null);
        if (session == null || session.getState() == BusinessValidationSession.State.STALE) {
            return Optional.empty();
        }
        Long ownerId = session.getProject().getOwner().getId();
        if (stale(ownerId, session)) {
            session.markStale();
            return Optional.empty();
        }
        MarketResearchService.CurrentView marketView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), marketTaskRunId);
        if (marketView.run().state().equals("FAILED")) {
            session.marketFailed();
            return Optional.empty();
        }
        if (marketView.version() == null) return Optional.empty();
        session.marketCompleted(marketView.version().id());
        return Optional.of(new MarketChainSource(session.getId(), session.getProject().getId(),
            marketView.version().id(), session.getSourceBmPlanRevision()));
    }

    /** Record the exact BM TaskRun created by the worker. */
    @Transactional
    public void businessModelQueued(String sessionId, String taskRunId, String commandKey) {
        BusinessValidationSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || session.getState() == BusinessValidationSession.State.STALE) return;
        if (session.getBmTaskRunId() == null) session.bmStarted(taskRunId, commandKey);
    }

    /** Project a committed BM version before the worker starts refinement. */
    @Transactional
    public Optional<CompletedSource> businessModelCompleted(String bmTaskRunId) {
        BusinessValidationSession session = sessions.findByBmTaskRunIdForUpdate(bmTaskRunId)
            .orElse(null);
        if (session == null || session.getState() == BusinessValidationSession.State.STALE) {
            return Optional.empty();
        }
        Long ownerId = session.getProject().getOwner().getId();
        if (stale(ownerId, session)) {
            session.markStale();
            return Optional.empty();
        }
        MarketResearchService.CurrentView bmView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), bmTaskRunId);
        if (bmView.run().state().equals("FAILED")) {
            session.bmFailed();
            return Optional.empty();
        }
        if (bmView.version() == null) return Optional.empty();
        session.completed(bmView.version().id());
        return Optional.of(completedSource(session));
    }

    /** Explicit manual recovery only; this is not an automatic chain authority. */
    @Transactional
    public CurrentView retryBm(Long ownerId, Long projectId, String commandKey,
            String correlationId) {
        owned(ownerId, projectId);
        String normalizedCommandKey = validCommandKey(commandKey);
        BusinessValidationSession latest = sessions
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        BusinessValidationSession session = sessions.findByIdForUpdate(latest.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (Objects.equals(normalizedCommandKey, session.getBmCommandIdempotencyKey())) {
            return view(ownerId, session);
        }
        if (session.getState() != BusinessValidationSession.State.BM_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "현재 시장 분석 결과를 기준으로 BM만 다시 실행할 수 없습니다.");
        }
        if (stale(ownerId, session)) {
            session.markStale();
            return view(ownerId, session);
        }
        startBmRecovery(ownerId, session, normalizedCommandKey, correlationId);
        return view(ownerId, session);
    }

    /** Projection repair only. It must not create BM or refinement tasks. */
    @Transactional
    public void reconcile(String sessionId) {
        BusinessValidationSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || !ACTIVE_STATES.contains(session.getState())) return;
        Long ownerId = session.getProject().getOwner().getId();
        if (stale(ownerId, session)) {
            session.markStale();
            return;
        }
        MarketResearchService.CurrentView marketView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getMarketTaskRunId());
        if (marketView.run().state().equals("FAILED")) {
            session.marketFailed();
            return;
        }
        if (marketView.version() == null) return;
        if (session.getMarketVersionId() == null) session.marketCompleted(marketView.version().id());

        if (session.getBmTaskRunId() == null) {
            MarketResearchRun exact = marketRuns
                .findTopByProjectIdAndKindAndSourceRunTaskRunIdAndSourceBmPlanRevisionAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                    session.getProject().getId(), MarketResearchRun.Kind.BM,
                    session.getMarketTaskRunId(), session.getSourceBmPlanRevision())
                .orElse(null);
            if (exact == null) return;
            session.bmStarted(exact.getTaskRun().getId(), "projection-repair-" + exact.getTaskRun().getId());
        }
        MarketResearchService.CurrentView bmView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getBmTaskRunId());
        if (bmView.run().state().equals("FAILED")) session.bmFailed();
        else if (bmView.version() != null) session.completed(bmView.version().id());
    }

    public List<String> activeSessionIds() { return sessions.findActiveIds(ACTIVE_STATES); }

    private void startBmRecovery(Long ownerId, BusinessValidationSession session,
            String commandKey, String correlationId) {
        var bm = market.startBmFromVersionAtPlanRevision(ownerId,
            session.getProject().getId(), session.getMarketVersionId(),
            session.getSourceBmPlanRevision(), derivedKey("bm", commandKey), correlationId);
        if (bm.isEmpty()) {
            session.markStale();
            return;
        }
        session.bmStarted(bm.get().taskRunId(), commandKey);
    }

    private CurrentView view(Long ownerId, BusinessValidationSession session) {
        MarketResearchService.CurrentView marketView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getMarketTaskRunId());
        MarketResearchService.CurrentView bmView = session.getBmTaskRunId() == null ? null
            : market.currentForTaskRun(ownerId, session.getProject().getId(), session.getBmTaskRunId());
        boolean stale = session.getState() == BusinessValidationSession.State.STALE
            || stale(session, marketView, bmView);
        String state = effectiveState(marketView, bmView, stale);
        return new CurrentView(session.getId(), state, stale, stage(marketView), stage(bmView), actions(state));
    }

    private static String effectiveState(MarketResearchService.CurrentView marketView,
            MarketResearchService.CurrentView bmView, boolean stale) {
        if (stale) return "STALE";
        if (marketView.run().state().equals("FAILED")) return "MARKET_FAILED";
        if (marketView.version() == null) return "MARKET_RUNNING";
        if (bmView == null) return "MARKET_COMPLETED";
        if (bmView.run().state().equals("FAILED")) return "BM_FAILED";
        if (bmView.version() == null) return "BM_RUNNING";
        return "COMPLETED";
    }

    private boolean stale(Long ownerId, BusinessValidationSession session) {
        MarketResearchService.CurrentView marketView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getMarketTaskRunId());
        MarketResearchService.CurrentView bmView = session.getBmTaskRunId() == null ? null
            : market.currentForTaskRun(ownerId, session.getProject().getId(), session.getBmTaskRunId());
        return stale(session, marketView, bmView);
    }

    private boolean stale(BusinessValidationSession session,
            MarketResearchService.CurrentView marketView,
            MarketResearchService.CurrentView bmView) {
        MarketResearchService.SourceView source = marketView.source();
        return marketView.stale() || (bmView != null && bmView.stale()) || source == null
            || !Objects.equals(source.marketSeedSnapshotId(), session.getSourceMarketSeedSnapshotId())
            || !Objects.equals(source.portfolioSelectionId(), session.getSourcePortfolioSelectionId())
            || !Objects.equals(source.selectionRevision(), session.getSourceSelectionRevision())
            || bmPlanChanged(session);
    }

    private boolean bmPlanChanged(BusinessValidationSession session) {
        Integer pinnedRevision = session.getSourceBmPlanRevision();
        return pinnedRevision != null
            && bmPlans.current(session.getProject().getId()).revision() != pinnedRevision;
    }

    private static StageView stage(MarketResearchService.CurrentView value) {
        if (value == null || value.run() == null) return new StageView("WAITING", false, null, null, false);
        return new StageView(value.run().state(), value.version() != null,
            value.version() == null ? null : value.version().result(),
            value.run().errorCode(), value.run().retryable());
    }

    private static List<String> actions(String state) {
        return switch (state) {
            case "NOT_STARTED" -> List.of("START");
            case "MARKET_FAILED", "STALE" -> List.of("RERUN");
            case "BM_FAILED" -> List.of("RETRY_BM");
            default -> List.of();
        };
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private Project ownedForUpdate(Long ownerId, Long projectId) {
        return projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private MarketResearchRun exactRun(Long projectId, String taskRunId) {
        return marketRuns.findByTaskRunIdAndDeletedAtIsNull(taskRunId)
            .filter(value -> Objects.equals(value.getProject().getId(), projectId))
            .orElseThrow(() -> new IllegalStateException("Market TaskRun lineage missing"));
    }

    private CompletedSource completedSource(BusinessValidationSession session) {
        return new CompletedSource(session.getId(), session.getMarketVersionId(),
            session.getBmVersionId(), session.getSourceMarketSeedSnapshotId(),
            session.getSourcePortfolioSelectionId(), session.getSourceSelectionRevision(),
            session.getSourceBmPlanRevision(), session.getCanonicalInputHash());
    }

    private static String derivedKey(String purpose, String source) {
        return "bv-" + purpose + "-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String validCommandKey(String source) {
        if (source == null || source.isBlank() || source.strip().length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return source.strip();
    }

    public record MarketChainSource(String businessValidationSessionId, Long projectId,
                                    Long marketVersionId, Integer bmPlanRevision) { }
    public record StageView(String state, boolean completed,
                            tools.jackson.databind.JsonNode result,
                            String errorCode, boolean retryable) { }
    public record CompletedSource(String businessValidationSessionId,
                                  Long marketVersionId, Long bmVersionId,
                                  String marketSeedSnapshotId, Long selectionId,
                                  Integer selectionRevision, Integer bmPlanRevision,
                                  String canonicalInputHash) { }
    public record CurrentView(String businessValidationSessionId, String state, boolean stale,
                              StageView market, StageView businessModel, List<String> actions) {
        static CurrentView notStarted() {
            return new CurrentView(null, "NOT_STARTED", false, stage(null), stage(null), List.of("START"));
        }
    }
}
