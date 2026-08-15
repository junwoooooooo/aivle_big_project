package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelectionStatus;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.market.ledger.MarketLedgerArtifactService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketResearchStartGateTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ConceptPortfolioSelectionRepository selections = mock(ConceptPortfolioSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository seeds = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final MarketResearchRunRepository runs = mock(MarketResearchRunRepository.class);
    private final MarketResearchVersionRepository versions = mock(MarketResearchVersionRepository.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
    private final MarketResearchInputFactory inputs = mock(MarketResearchInputFactory.class);
    private final BmPlanPreparationService plans = mock(BmPlanPreparationService.class);
    private final ResearchCompetitorSeedService competitorSeeds = mock(ResearchCompetitorSeedService.class);
    private final MarketLedgerArtifactService ledgerArtifacts = mock(MarketLedgerArtifactService.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final MarketResearchService service = new MarketResearchService(
        projects, selections, seeds, runs, versions, taskRuns, hasher, inputs, plans,
        competitorSeeds, ledgerArtifacts, events, new ObjectMapper());

    @Test
    void ownedReadyCurrentSelectionAndSeedCreateAQueuedMarketTask() {
        Project project = mock(Project.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        TaskRun task = mock(TaskRun.class);
        when(project.getId()).thenReturn(41L);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(selection.getId()).thenReturn(9L);
        when(selection.getConceptId()).thenReturn("arbitrary-concept");
        when(selection.getHypothesisRevision()).thenReturn(3);
        when(selection.getStatus()).thenReturn(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        when(seeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(9L)).thenReturn(Optional.of(seed));
        when(seed.getSourceType()).thenReturn("CONCEPT_PORTFOLIO_V2");
        when(seed.getId()).thenReturn("seed-9");
        var currentPlan = new BmPlanPreparationService.PlanView(
            new ObjectMapper().createObjectNode(), new ObjectMapper().createObjectNode(), 0);
        when(plans.current(41L)).thenReturn(currentPlan);
        when(inputs.full(seed, selection, "2026-08-11", null, currentPlan.constraints())).thenReturn("{}");
        when(hasher.hash(any(), any(), any(), any())).thenReturn("sha256:" + "a".repeat(64));
        when(task.getId()).thenReturn("task-1");
        when(task.getState()).thenReturn(TaskRunState.QUEUED);
        when(taskRuns.createWithDisposition(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
            .thenReturn(new TaskRunService.CreateResult(task, true, false));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.startFull(7L, 41L, "2026-08-11", "idempotency", "correlation");

        assertThat(result.taskRunId()).isEqualTo("task-1");
        assertThat(result.taskState()).isEqualTo("QUEUED");
        verify(inputs).full(seed, selection, "2026-08-11", null, currentPlan.constraints());
    }

    @Test
    void ownershipAndReadinessFailuresDoNotFallBackToASample() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startFull(7L, 41L, null, "key", "correlation"))
            .isInstanceOf(BusinessException.class);

        Project project = mock(Project.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(selection.getStatus()).thenReturn(ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION);
        assertThatThrownBy(() -> service.startFull(7L, 41L, null, "key-2", "correlation"))
            .isInstanceOf(BusinessException.class);
    }
}
