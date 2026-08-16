package com.aivle.backend.pipeline.businessvalidation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public CurrentView start(Long ownerId, Long projectId, String asOf,
            String commandKey, String correlationId) {
        Project project = owned(ownerId, projectId);
        String normalizedCommandKey = validCommandKey(commandKey);
        String marketKey = derivedKey("market", normalizedCommandKey);
        MarketResearchService.RunView started = market.startFull(
            ownerId, projectId, asOf, marketKey, correlationId);
        BusinessValidationSession session = sessions
            .findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(projectId, normalizedCommandKey)
            .orElseGet(() -> sessions.save(BusinessValidationSession.start(project,
                exactRun(projectId, started.taskRunId()), normalizedCommandKey)));
        if (!Objects.equals(session.getMarketTaskRunId(), started.taskRunId())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return view(ownerId, session);
    }

    @Transactional(readOnly = true)
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        return sessions.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .map(value -> view(ownerId, value))
            .orElseGet(CurrentView::notStarted);
    }

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
        if (Objects.equals(normalizedCommandKey, session.getBmCommandIdempotencyKey())) return view(ownerId, session);
        if (session.getState() != BusinessValidationSession.State.BM_FAILED || stale(ownerId, session)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "현재 시장 분석 결과를 기준으로 BM만 다시 실행할 수 없습니다.");
        }
        startBm(ownerId, session, normalizedCommandKey, correlationId);
        return view(ownerId, session);
    }

    @Transactional
    public void reconcile(String sessionId) {
        BusinessValidationSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || !ACTIVE_STATES.contains(session.getState())) return;
        Long ownerId = session.getProject().getOwner().getId();
        if (stale(ownerId, session)) return;
        MarketResearchService.CurrentView marketView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getMarketTaskRunId());
        if (marketView.run().state().equals("FAILED")) {
            session.marketFailed();
            return;
        }
        if (marketView.version() == null) return;
        if (session.getMarketVersionId() == null) session.marketCompleted(marketView.version().id());
        if (session.getBmTaskRunId() == null) {
            startBm(ownerId, session, derivedKey("bm-auto", session.getId()), session.getId());
            return;
        }
        MarketResearchService.CurrentView bmView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getBmTaskRunId());
        if (bmView.run().state().equals("FAILED")) session.bmFailed();
        else if (bmView.version() != null) session.completed(bmView.version().id());
    }

    public List<String> activeSessionIds() { return sessions.findActiveIds(ACTIVE_STATES); }

    private void startBm(Long ownerId, BusinessValidationSession session,
            String commandKey, String correlationId) {
        MarketResearchService.RunView bm = market.startBmFromVersion(ownerId,
            session.getProject().getId(), session.getMarketVersionId(),
            derivedKey("bm", commandKey), correlationId);
        session.bmStarted(bm.taskRunId(), commandKey);
    }

    private CurrentView view(Long ownerId, BusinessValidationSession session) {
        MarketResearchService.CurrentView marketView = market.currentForTaskRun(
            ownerId, session.getProject().getId(), session.getMarketTaskRunId());
        MarketResearchService.CurrentView bmView = session.getBmTaskRunId() == null ? null
            : market.currentForTaskRun(ownerId, session.getProject().getId(), session.getBmTaskRunId());
        boolean stale = stale(session, marketView, bmView);
        String state = effectiveState(marketView, bmView, stale);
        return new CurrentView(state, stale, stage(marketView), stage(bmView), actions(state));
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
            || !Objects.equals(source.selectionRevision(), session.getSourceSelectionRevision());
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

    private MarketResearchRun exactRun(Long projectId, String taskRunId) {
        return marketRuns.findByTaskRunIdAndDeletedAtIsNull(taskRunId)
            .filter(value -> Objects.equals(value.getProject().getId(), projectId))
            .orElseThrow(() -> new IllegalStateException("Market TaskRun lineage missing"));
    }

    private static String derivedKey(String purpose, String source) {
        return "bv-" + purpose + "-" + UUID.nameUUIDFromBytes(
            source.getBytes(StandardCharsets.UTF_8));
    }

    private static String validCommandKey(String source) {
        if (source == null || source.isBlank() || source.strip().length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return source.strip();
    }

    public record StageView(String state, boolean completed,
                            tools.jackson.databind.JsonNode result,
                            String errorCode, boolean retryable) { }
    public record CurrentView(String state, boolean stale, StageView market,
                              StageView businessModel, List<String> actions) {
        static CurrentView notStarted() {
            return new CurrentView("NOT_STARTED", false, stage(null), stage(null), List.of("START"));
        }
    }
}
