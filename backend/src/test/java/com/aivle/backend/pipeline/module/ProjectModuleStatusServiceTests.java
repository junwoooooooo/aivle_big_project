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
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectModuleStatusServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    private final ConceptPortfolioRunRepository conceptRuns = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository snapshots = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final ModuleRunRepository runs = mock(ModuleRunRepository.class);
    private final MarketingContentRepository marketing = mock(MarketingContentRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final TechOpsInputPreparationRepository techOpsPreparations = mock(TechOpsInputPreparationRepository.class);
    private final TechOpsInputSnapshotRepository techOpsSnapshots = mock(TechOpsInputSnapshotRepository.class);
    private final FinancialInputPreparationRepository financialPreparations = mock(FinancialInputPreparationRepository.class);
    private final FinancialInputSnapshotRepository financialSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(
        projects, briefs, conceptRuns, portfolioSelections, selections, snapshots, runs, marketing, marketingSources,
        techOpsPreparations, techOpsSnapshots, financialPreparations, financialSnapshots);

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
            PipelineModuleType.FINANCE, PipelineModuleType.MARKETING);
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
        assertThat(modules.get(6).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
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
    void exposesIndependentFinancialPreparationStatusAfterTechOpsSnapshotFinalization() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class); MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        TechOpsInputSnapshot techOpsSnapshot = mock(TechOpsInputSnapshot.class);
        FinancialInputPreparation preparation = mock(FinancialInputPreparation.class);
        when(selection.getId()).thenReturn(13L); when(seed.getId()).thenReturn("market-seed-1"); when(techOpsSnapshot.getId()).thenReturn("tech-1");
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(seed));
        when(techOpsSnapshots.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull("market-seed-1", 41L))
            .thenReturn(Optional.of(techOpsSnapshot));
        when(financialPreparations.findByProjectIdAndSourceTechOpsSnapshotIdAndDeletedAtIsNull(41L, "tech-1"))
            .thenReturn(Optional.of(preparation));

        var finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();

        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(finance.requiredInputs()).containsExactly("financialRequiredInputs");
        assertThat(finance.nextAction().route()).isEqualTo("/finance");
    }

    @Test
    void hidesProjectsNotOwnedByTheCurrentUser() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findAll(8L, 41L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
