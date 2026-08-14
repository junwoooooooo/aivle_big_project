package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelectionStatus;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentType;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.market.TwinSurveyRunRepository;
import com.aivle.backend.pipeline.market.TwinSurveyVersionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsAdvisoryReportRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProjectModuleStatusServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    private final ConceptPortfolioRunRepository conceptRuns = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository snapshots = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final ModuleRunRepository runs = mock(ModuleRunRepository.class);
    private final MarketResearchRunRepository marketRuns = mock(MarketResearchRunRepository.class);
    private final MarketResearchVersionRepository marketVersions = mock(MarketResearchVersionRepository.class);
    private final TwinSurveyRunRepository twinRuns = mock(TwinSurveyRunRepository.class);
    private final TwinSurveyVersionRepository twinVersions = mock(TwinSurveyVersionRepository.class);
    private final MarketingContentRepository marketing = mock(MarketingContentRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final TechOpsInputPreparationRepository techOpsPreparations = mock(TechOpsInputPreparationRepository.class);
    private final TechOpsInputSnapshotRepository techOpsSnapshots = mock(TechOpsInputSnapshotRepository.class);
    private final TechOpsAdvisoryReportRepository techOpsAdvisories = mock(TechOpsAdvisoryReportRepository.class);
    private final FinancialInputPreparationRepository financialPreparations = mock(FinancialInputPreparationRepository.class);
    private final FinancialInputSnapshotRepository financialSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final TaskRunRepository taskRuns = mock(TaskRunRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(
        projects, briefs, conceptRuns, portfolioSelections, selections, snapshots, runs,
        marketRuns, marketVersions, twinRuns, twinVersions, marketing, marketingSources,
        techOpsPreparations, techOpsSnapshots, techOpsAdvisories, financialPreparations, financialSnapshots, taskRuns);

    @Test
    void derivesIdeaAndConceptFromCanonicalDomainsWithoutProjectDescription() {
        Project project = mock(Project.class);
        IdeaBrief brief = mock(IdeaBrief.class);
        ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 7, 10, 0);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(briefs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(brief));
        when(brief.getStatus()).thenReturn(IdeaBriefStatus.CONFIRMED);
        when(brief.getConfirmedSnapshotId()).thenReturn("brief-snapshot");
        when(brief.getUpdatedAt()).thenReturn(updatedAt);
        when(conceptRuns.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getId()).thenReturn("run-1");
        when(run.getActiveTaskRunId()).thenReturn("task-1");
        when(run.getSourceIdeaBrief()).thenReturn(brief);
        when(brief.getId()).thenReturn("brief-snapshot");
        when(run.getProductStatus()).thenReturn(ConceptPortfolioRunStatus.RUNNING);
        when(run.getProducedConceptCount()).thenReturn(3);

        var modules = service.findAll(7L, 41L);

        assertThat(modules).extracting(ProjectModuleStatusResponse::module).containsExactly(
            PipelineModuleType.IDEA, PipelineModuleType.CONCEPT_PORTFOLIO,
            PipelineModuleType.MARKET_ANALYSIS, PipelineModuleType.BUSINESS_MODEL, PipelineModuleType.TECH_OPS,
            PipelineModuleType.FINANCE, PipelineModuleType.TWIN_SURVEY, PipelineModuleType.MARKETING);
        assertThat(modules.get(0).status()).isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(modules.get(0).confirmedSnapshotId()).isEqualTo("brief-snapshot");
        assertThat(modules.get(1).status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(modules.get(1).activeTaskRunId()).isEqualTo("task-1");
        assertThat(modules.get(1).activeJobId()).isEqualTo("task-1");
        assertThat(modules.get(1).eligibleCount()).isEqualTo(3L);
        verify(project, never()).getDescription();
    }

    @Test
    void returnsNeedsInputWhenNoIdeaBriefExists() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        var modules = service.findAll(7L, 41L);
        assertThat(modules.get(0).status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(modules.get(1).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(3).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(4).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(5).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(6).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(7).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
    }

    @Test
    void mapsCurrentV2SelectionAndMarketSeedToCanonicalPortfolioModule() {
        Project project = mock(Project.class);
        ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
        IdeaBrief brief = mock(IdeaBrief.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(briefs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(brief));
        when(brief.getStatus()).thenReturn(IdeaBriefStatus.CONFIRMED);
        when(brief.getConfirmedSnapshotId()).thenReturn("brief-v2");
        when(conceptRuns.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getSourceIdeaBrief()).thenReturn(brief);
        when(brief.getId()).thenReturn("brief-v2");
        when(run.getProductStatus()).thenReturn(ConceptPortfolioRunStatus.RESULTS_AVAILABLE);
        when(run.getProducedConceptCount()).thenReturn(2);
        when(portfolioSelections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(selection.getId()).thenReturn(17L);
        when(selection.getStatus()).thenReturn(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        when(snapshots.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(17L)).thenReturn(Optional.of(seed));
        when(seed.getId()).thenReturn("seed-v2");

        var modules = service.findAll(7L, 41L);

        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.CONCEPT_PORTFOLIO)
            .findFirst().orElseThrow().status()).isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.MARKET_ANALYSIS)
            .findFirst().orElseThrow().sourceSnapshotId()).isEqualTo("seed-v2");
        verify(selections, never()).findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L);
    }

    @Test
    void exposesIndependentTechOpsPreparationStatusAfterMarketSeedFinalization() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class); MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        TechOpsInputPreparation preparation = mock(TechOpsInputPreparation.class);
        when(selection.getId()).thenReturn(13L); when(seed.getId()).thenReturn("market-seed-1");
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(seed));
        when(techOpsPreparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "market-seed-1"))
            .thenReturn(Optional.of(preparation));

        var techOps = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.TECH_OPS).findFirst().orElseThrow();

        assertThat(techOps.status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(techOps.requiredInputs()).containsExactly("techOpsRequiredFacts", "techOpsRequiredDecisions");
        assertThat(techOps.nextAction().route()).isEqualTo("/tech-ops");
    }

    @Test
    void exposesFinancialPreparationStatusWithoutTechOpsSnapshot() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class); MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        FinancialInputPreparation preparation = mock(FinancialInputPreparation.class);
        var marketRun = mock(com.aivle.backend.pipeline.market.MarketResearchRun.class);
        var bmRun = mock(com.aivle.backend.pipeline.market.MarketResearchRun.class);
        var marketVersion = mock(com.aivle.backend.pipeline.market.MarketResearchVersion.class);
        var bmVersion = mock(com.aivle.backend.pipeline.market.MarketResearchVersion.class);
        var marketTask = mock(com.aivle.backend.taskrun.domain.TaskRun.class);
        var bmTask = mock(com.aivle.backend.taskrun.domain.TaskRun.class);
        when(selection.getId()).thenReturn(13L); when(seed.getId()).thenReturn("market-seed-1");
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(seed));
        when(marketRun.getId()).thenReturn(300L);
        when(marketRun.getSourceMarketSeedSnapshotId()).thenReturn("market-seed-1");
        when(marketRun.getTaskRun()).thenReturn(marketTask); when(marketTask.getState()).thenReturn(com.aivle.backend.taskrun.domain.TaskRunState.SUCCEEDED);
        when(marketTask.getId()).thenReturn("market-task");
        when(marketVersion.getId()).thenReturn(101L); when(marketVersion.getSourceRun()).thenReturn(marketRun);
        when(bmRun.getId()).thenReturn(301L); when(bmRun.getSourceMarketVersionId()).thenReturn(101L);
        when(bmRun.getTaskRun()).thenReturn(bmTask); when(bmTask.getState()).thenReturn(com.aivle.backend.taskrun.domain.TaskRunState.SUCCEEDED);
        when(bmTask.getId()).thenReturn("bm-task");
        when(bmVersion.getId()).thenReturn(201L);
        when(marketRuns.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, com.aivle.backend.pipeline.market.MarketResearchRun.Kind.FULL)).thenReturn(Optional.of(marketRun));
        when(marketRuns.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, com.aivle.backend.pipeline.market.MarketResearchRun.Kind.BM)).thenReturn(Optional.of(bmRun));
        when(marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
            41L, com.aivle.backend.pipeline.market.MarketResearchRun.Kind.FULL)).thenReturn(Optional.of(marketVersion));
        when(marketVersions.findBySourceRunIdAndDeletedAtIsNull(301L)).thenReturn(Optional.of(bmVersion));
        when(financialPreparations.findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            41L, 101L, 201L))
            .thenReturn(Optional.of(preparation));
        when(preparation.getId()).thenReturn("finance-prep-1");

        var finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();

        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(finance.requiredInputs()).containsExactly("financialRequiredInputs");
        assertThat(finance.nextAction().route()).isEqualTo("/finance");

        TaskRun estimate = mock(TaskRun.class);
        when(estimate.getId()).thenReturn("finance-estimate-task");
        when(estimate.getSubjectType()).thenReturn("FINANCIAL_PREPARATION");
        when(estimate.getSubjectId()).thenReturn("finance-prep-1");
        when(estimate.getState()).thenReturn(TaskRunState.RUNNING);
        when(taskRuns.findFirstByProjectIdAndTaskTypeInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, List.of(TaskType.FINANCE_ESTIMATE))).thenReturn(Optional.of(estimate));
        finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();
        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(finance.activeTaskRunId()).isEqualTo("finance-estimate-task");

        when(estimate.getState()).thenReturn(TaskRunState.FAILED);
        finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();
        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(finance.activeTaskRunId()).isNull();

        var financialSnapshot = mock(com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot.class);
        when(financialSnapshot.getId()).thenReturn("finance-snapshot-1");
        when(financialSnapshots.findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
            41L, 101L, 201L)).thenReturn(Optional.of(financialSnapshot));
        TaskRun report = mock(TaskRun.class);
        when(report.getId()).thenReturn("finance-report-task");
        when(report.getState()).thenReturn(TaskRunState.RUNNING);
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, "FINANCIAL_ANALYSIS_REPORT", "finance-snapshot-1")).thenReturn(Optional.of(report));
        finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();
        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(finance.activeTaskRunId()).isEqualTo("finance-report-task");

        var previousMarketRun = mock(com.aivle.backend.pipeline.market.MarketResearchRun.class);
        when(previousMarketRun.getId()).thenReturn(299L);
        when(previousMarketRun.getSourceMarketSeedSnapshotId()).thenReturn("market-seed-1");
        when(marketVersion.getSourceRun()).thenReturn(previousMarketRun);
        finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();
        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.NOT_READY);
    }

    @Test
    void overlaysTwinDraftOnlyWhileActiveAndKeepsUnderlyingReadyOnFailure() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L))
            .thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(selection.getId()).thenReturn(13L);
        when(seed.getId()).thenReturn("market-seed-1");
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L))
            .thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L))
            .thenReturn(Optional.of(seed));
        TaskRun draft = mock(TaskRun.class);
        when(draft.getId()).thenReturn("twin-draft-task");
        when(draft.getSubjectType()).thenReturn("TWIN_STIMULUS_DRAFT");
        when(draft.getSubjectId()).thenReturn("41");
        when(draft.getState()).thenReturn(TaskRunState.RUNNING);
        when(taskRuns.findFirstByProjectIdAndTaskTypeInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, List.of(TaskType.TWIN_STIMULUS_DRAFT))).thenReturn(Optional.of(draft));

        var twin = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.TWIN_SURVEY).findFirst().orElseThrow();
        assertThat(twin.status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(twin.activeTaskRunId()).isEqualTo("twin-draft-task");

        var surveyRun = mock(com.aivle.backend.pipeline.market.TwinSurveyRun.class);
        TaskRun surveyTask = mock(TaskRun.class);
        when(surveyRun.getTaskRun()).thenReturn(surveyTask);
        when(surveyRun.getSourceMarketSeedSnapshotId()).thenReturn("market-seed-1");
        when(surveyTask.getId()).thenReturn("twin-survey-task");
        when(surveyTask.getState()).thenReturn(TaskRunState.RUNNING);
        when(twinRuns.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(surveyRun));
        twin = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.TWIN_SURVEY).findFirst().orElseThrow();
        assertThat(twin.status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(twin.activeTaskRunId()).isEqualTo("twin-survey-task");

        when(twinRuns.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.empty());
        when(draft.getState()).thenReturn(TaskRunState.FAILED);
        twin = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.TWIN_SURVEY).findFirst().orElseThrow();
        assertThat(twin.status()).isEqualTo(PipelineModuleStatus.READY);
        assertThat(twin.activeTaskRunId()).isNull();
    }

    @Test
    void hidesProjectsNotOwnedByTheCurrentUser() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findAll(8L, 41L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void keepsCompletedMarketingContentCompleteWhenTheLatestVisualTaskFails() {
        MarketingContent content = MarketingContent.queued("content-1", 41L, "source-1", "sha256:source",
            "{}", "{}", MarketingContentType.SOCIAL_POST, "SOCIAL", "title", 7L);
        content.start();
        content.completeRevision();
        TaskRun visualTask = mock(TaskRun.class);
        when(visualTask.getState()).thenReturn(TaskRunState.FAILED);

        PipelineModuleStatus status = ReflectionTestUtils.invokeMethod(
            service, "marketingStatus", content, "source-1", visualTask);

        assertThat(status).isEqualTo(PipelineModuleStatus.COMPLETED);
    }
}
