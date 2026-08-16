package com.aivle.backend.pipeline.businessvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.user.entity.User;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessValidationCoordinatorTests {

    @Mock ProjectRepository projects;
    @Mock BusinessValidationSessionRepository sessions;
    @Mock MarketResearchRunRepository runs;
    @Mock MarketResearchService market;
    @Mock BmPlanPreparationService bmPlans;
    private BusinessValidationCoordinator coordinator;
    private Project project;

    @BeforeEach
    void setUp() {
        coordinator = new BusinessValidationCoordinator(projects, sessions, runs, market, bmPlans);
        project = mock(Project.class);
        User owner = mock(User.class);
        when(project.getId()).thenReturn(41L);
        when(project.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(7L);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
        when(bmPlans.current(41L)).thenReturn(plan(3));
    }

    @Test
    void startEnqueuesExistingFullMarketTask() {
        MarketResearchRun run = marketRun("market-task");
        when(market.startFull(eq(7L), eq(41L), anyString(), anyString(), anyString()))
            .thenReturn(runView("market-task", "QUEUED"));
        when(runs.findByTaskRunIdAndDeletedAtIsNull("market-task")).thenReturn(Optional.of(run));
        when(sessions.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(41L, "command-1"))
            .thenReturn(Optional.empty());
        when(sessions.findTopByProjectIdAndStateInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            eq(41L), anyCollection())).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(market.currentForTaskRun(7L, 41L, "market-task")).thenReturn(current("QUEUED", null, false));

        var result = coordinator.start(7L, 41L, "2026-08-16", "command-1", "request-1");

        assertThat(result.state()).isEqualTo("MARKET_RUNNING");
        verify(market).startFull(eq(7L), eq(41L), eq("2026-08-16"), startsWith("bv-market-"), eq("request-1"));
        verify(market, never()).startBmFromVersion(anyLong(), anyLong(), anyLong(), anyString(), anyString());
        var saved = ArgumentCaptor.forClass(BusinessValidationSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getSourceBmPlanRevision()).isEqualTo(3);
    }

    @Test
    void marketSuccessStartsBmFromExactVersion() {
        BusinessValidationSession session = session(BusinessValidationSession.State.MARKET_RUNNING, null, null);
        when(session.getMarketVersionId()).thenReturn(null, 91L);
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(session));
        when(market.currentForTaskRun(7L, 41L, "market-task"))
            .thenReturn(current("SUCCEEDED", 91L, false));
        when(market.startBmFromVersionAtPlanRevision(eq(7L), eq(41L), eq(91L), eq(3),
            anyString(), eq("session-1"))).thenReturn(Optional.of(runView("bm-task", "QUEUED")));

        coordinator.reconcile("session-1");

        verify(session).marketCompleted(91L);
        verify(session).bmStarted(eq("bm-task"), anyString());
    }

    @Test
    void marketFailureNeverStartsBm() {
        BusinessValidationSession session = session(BusinessValidationSession.State.MARKET_RUNNING, null, null);
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(session));
        when(market.currentForTaskRun(7L, 41L, "market-task"))
            .thenReturn(current("FAILED", null, false));

        coordinator.reconcile("session-1");

        verify(session).marketFailed();
        verify(market, never()).startBmFromVersion(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void bmFailureKeepsMarketVersionAndMarksOnlyBmFailed() {
        BusinessValidationSession session = session(BusinessValidationSession.State.BM_RUNNING, 91L, "bm-task");
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(session));
        when(market.currentForTaskRun(7L, 41L, "market-task")).thenReturn(current("SUCCEEDED", 91L, false));
        when(market.currentForTaskRun(7L, 41L, "bm-task")).thenReturn(current("FAILED", null, false));

        coordinator.reconcile("session-1");

        verify(session).bmFailed();
        verify(session, never()).marketFailed();
        verify(market, never()).startFull(anyLong(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    void retryBmUsesPreservedMarketVersionWithoutRerunningMarket() {
        BusinessValidationSession latest = session(BusinessValidationSession.State.BM_FAILED, 91L, "old-bm");
        when(sessions.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(latest));
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(latest));
        when(market.currentForTaskRun(7L, 41L, "market-task")).thenReturn(current("SUCCEEDED", 91L, false));
        when(market.currentForTaskRun(7L, 41L, "old-bm")).thenReturn(current("FAILED", null, false));
        when(market.startBmFromVersionAtPlanRevision(eq(7L), eq(41L), eq(91L), eq(3),
            anyString(), eq("request-2"))).thenReturn(Optional.of(runView("new-bm", "QUEUED")));
        coordinator.retryBm(7L, 41L, "retry-1", "request-2");

        verify(market, never()).startFull(anyLong(), anyLong(), any(), anyString(), anyString());
        verify(market).startBmFromVersionAtPlanRevision(eq(7L), eq(41L), eq(91L), eq(3),
            anyString(), eq("request-2"));
        verify(latest).bmStarted("new-bm", "retry-1");
    }

    @Test
    void staleActiveSessionIsPersistedAndExcludedFromFutureActiveScans() {
        BusinessValidationSession session = session(BusinessValidationSession.State.MARKET_RUNNING, null, null);
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(session));
        when(market.currentForTaskRun(7L, 41L, "market-task"))
            .thenReturn(current("RUNNING", null, true));
        when(sessions.findActiveIds(anyCollection())).thenReturn(List.of());

        coordinator.reconcile("session-1");
        coordinator.activeSessionIds();

        verify(session).markStale();
        verify(sessions).findActiveIds(argThat(states ->
            !states.contains(BusinessValidationSession.State.STALE)));
    }

    @Test
    void differentCommandReturnsExistingActiveSessionWithoutNewMarketRun() {
        BusinessValidationSession active = session(BusinessValidationSession.State.MARKET_RUNNING, null, null);
        when(sessions.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(41L, "command-2"))
            .thenReturn(Optional.empty());
        when(sessions.findTopByProjectIdAndStateInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            eq(41L), anyCollection())).thenReturn(Optional.of(active));
        when(market.currentForTaskRun(7L, 41L, "market-task"))
            .thenReturn(current("RUNNING", null, false));

        var result = coordinator.start(7L, 41L, "2026-08-16", "command-2", "request-2");

        assertThat(result.state()).isEqualTo("MARKET_RUNNING");
        verify(projects).findByIdForUpdate(41L);
        verify(market, never()).startFull(anyLong(), anyLong(), any(), anyString(), anyString());
        verify(sessions, never()).save(any());
    }

    @Test
    void changedBmPlanStopsContinuationAndPersistsStale() {
        BusinessValidationSession session = session(BusinessValidationSession.State.MARKET_RUNNING, null, null);
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(session));
        when(bmPlans.current(41L)).thenReturn(plan(4));
        when(market.currentForTaskRun(7L, 41L, "market-task"))
            .thenReturn(current("SUCCEEDED", 91L, false));

        coordinator.reconcile("session-1");

        verify(session).markStale();
        verify(market, never()).startBmFromVersionAtPlanRevision(
            anyLong(), anyLong(), anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void changedBmPlanStopsBmOnlyRetryAndPersistsStale() {
        BusinessValidationSession session = session(BusinessValidationSession.State.BM_FAILED, 91L, "old-bm");
        when(sessions.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate("session-1")).thenReturn(Optional.of(session));
        when(bmPlans.current(41L)).thenReturn(plan(4));
        when(market.currentForTaskRun(7L, 41L, "market-task"))
            .thenReturn(current("SUCCEEDED", 91L, false));
        when(market.currentForTaskRun(7L, 41L, "old-bm"))
            .thenReturn(current("FAILED", null, false));

        var result = coordinator.retryBm(7L, 41L, "retry-2", "request-3");

        assertThat(result.state()).isEqualTo("STALE");
        verify(session).markStale();
        verify(market, never()).startBmFromVersionAtPlanRevision(
            anyLong(), anyLong(), anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void changedSourceMakesLatestSessionStaleWithoutDeletingResults() {
        BusinessValidationSession latest = session(BusinessValidationSession.State.COMPLETED, 91L, "bm-task");
        when(sessions.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(latest));
        when(market.currentForTaskRun(7L, 41L, "market-task")).thenReturn(current("SUCCEEDED", 91L, true));
        when(market.currentForTaskRun(7L, 41L, "bm-task")).thenReturn(current("SUCCEEDED", 92L, true));

        var result = coordinator.current(7L, 41L);

        assertThat(result.state()).isEqualTo("STALE");
        assertThat(result.market().result()).isNotNull();
        assertThat(result.businessModel().result()).isNotNull();
    }

    private MarketResearchRun marketRun(String taskId) {
        MarketResearchRun run = mock(MarketResearchRun.class);
        TaskRun task = mock(TaskRun.class);
        when(run.getProject()).thenReturn(project);
        when(run.getTaskRun()).thenReturn(task);
        when(task.getId()).thenReturn(taskId);
        when(run.getSourceMarketSeedSnapshotId()).thenReturn("seed-1");
        when(run.getSourcePortfolioSelectionId()).thenReturn(31L);
        when(run.getSourceSelectionRevision()).thenReturn(4);
        when(run.getInputSnapshotHash()).thenReturn("sha256:market");
        return run;
    }

    private BusinessValidationSession session(BusinessValidationSession.State state,
            Long marketVersionId, String bmTaskId) {
        BusinessValidationSession session = mock(BusinessValidationSession.class);
        User owner = mock(User.class);
        when(session.getId()).thenReturn("session-1");
        when(session.getProject()).thenReturn(project);
        when(project.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(7L);
        when(session.getState()).thenReturn(state);
        when(session.getMarketTaskRunId()).thenReturn("market-task");
        when(session.getMarketVersionId()).thenReturn(marketVersionId);
        when(session.getBmTaskRunId()).thenReturn(bmTaskId);
        when(session.getSourceMarketSeedSnapshotId()).thenReturn("seed-1");
        when(session.getSourcePortfolioSelectionId()).thenReturn(31L);
        when(session.getSourceSelectionRevision()).thenReturn(4);
        when(session.getSourceBmPlanRevision()).thenReturn(3);
        return session;
    }

    private static BmPlanPreparationService.PlanView plan(int revision) {
        var factory = tools.jackson.databind.node.JsonNodeFactory.instance;
        return new BmPlanPreparationService.PlanView(factory.objectNode(), factory.objectNode(), revision);
    }

    private static MarketResearchService.RunView runView(String taskId, String state) {
        return new MarketResearchService.RunView(1L, "FULL", state, taskId, state, null, true);
    }

    private static MarketResearchService.CurrentView current(String state, Long versionId, boolean stale) {
        var source = new MarketResearchService.SourceView("concept-1", "사업안", 31L, 4, "seed-1");
        var version = versionId == null ? null : new MarketResearchService.VersionView(versionId, "FULL", 1,
            tools.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("marker", versionId),
            1, 0, null, null, null, null, null);
        return new MarketResearchService.CurrentView(runView("unused", state), version, source, stale);
    }
}
