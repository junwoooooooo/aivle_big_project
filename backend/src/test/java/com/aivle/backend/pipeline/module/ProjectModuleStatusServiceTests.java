package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.domain.ConceptSlotStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptSlotRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.journey.MarketResearchRunRepository;
import com.aivle.backend.journey.TwinSurveyRunRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.finance.repository.FinancialAnalysisReportRepository;
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
    private final ConceptFactoryRunRepository conceptRuns = mock(ConceptFactoryRunRepository.class);
    private final ConceptSlotRepository slots = mock(ConceptSlotRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository snapshots = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final ModuleRunRepository runs = mock(ModuleRunRepository.class);
    private final MarketingContentRepository marketing = mock(MarketingContentRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final TechOpsInputPreparationRepository techOpsPreparations = mock(TechOpsInputPreparationRepository.class);
    private final TechOpsInputSnapshotRepository techOpsSnapshots = mock(TechOpsInputSnapshotRepository.class);
    private final FinancialInputPreparationRepository financialPreparations = mock(FinancialInputPreparationRepository.class);
    private final FinancialInputSnapshotRepository financialSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final FinancialAnalysisReportRepository financialAnalysisReports = mock(FinancialAnalysisReportRepository.class);
    private final MarketResearchRunRepository marketResearchRuns = mock(MarketResearchRunRepository.class);
    private final TwinSurveyRunRepository twinSurveyRuns = mock(TwinSurveyRunRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(
        projects, briefs, conceptRuns, slots, selections, snapshots, runs, marketing, marketingSources,
        techOpsPreparations, techOpsSnapshots, financialPreparations, financialSnapshots,
        financialAnalysisReports,
        marketResearchRuns, twinSurveyRuns);

    @Test
    void derivesIdeaAndConceptFromCanonicalDomainsWithoutProjectDescription() {
        Project project = mock(Project.class);
        IdeaBrief brief = mock(IdeaBrief.class);
        ConceptFactoryRun run = mock(ConceptFactoryRun.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 7, 10, 0);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(briefs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(brief));
        when(brief.getStatus()).thenReturn(IdeaBriefStatus.CONFIRMED);
        when(brief.getConfirmedSnapshotId()).thenReturn("brief-snapshot");
        when(brief.getUpdatedAt()).thenReturn(updatedAt);
        when(conceptRuns.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getId()).thenReturn("run-1");
        when(run.getTaskRunId()).thenReturn("task-1");
        when(run.getSourceIdeaBriefSnapshotId()).thenReturn("brief-snapshot");
        when(run.getStatus()).thenReturn(ConceptFactoryRunStatus.GENERATING);
        when(slots.countByRunIdAndStatusAndDeletedAtIsNull("run-1", ConceptSlotStatus.ELIGIBLE)).thenReturn(3L);

        var modules = service.findAll(7L, 41L);

        assertThat(modules).extracting(ProjectModuleStatusResponse::module).containsExactly(
            PipelineModuleType.IDEA, PipelineModuleType.CONCEPT_FACTORY, PipelineModuleType.CONCEPT_SELECTION,
            PipelineModuleType.MARKET_ANALYSIS, PipelineModuleType.BUSINESS_MODEL, PipelineModuleType.TECH_OPS,
            PipelineModuleType.FINANCE, PipelineModuleType.PANEL_SURVEY, PipelineModuleType.MARKETING);
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
        // 8번은 마케팅이다 — 트윈이 7번으로 끼어들면서 인덱스가 밀렸다. 밀린 자리도 계속 센다.
        assertThat(modules.get(8).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
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
