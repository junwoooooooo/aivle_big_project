package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.finance.repository.FinancialAnalysisReportRepository;
import com.aivle.backend.journey.MarketResearchRunRepository;
import com.aivle.backend.journey.TwinSurveyRunRepository;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport;
import com.aivle.backend.launchreadiness.repository.ProfessionalAnalysisReportRepository;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectModuleStatusServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    private final ConceptPortfolioRunRepository conceptRuns = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final MarketingContentRepository marketing = mock(MarketingContentRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final ProfessionalAnalysisReportRepository professionalReports = mock(ProfessionalAnalysisReportRepository.class);
    private final FinancialInputPreparationRepository financialPreparations = mock(FinancialInputPreparationRepository.class);
    private final FinancialInputSnapshotRepository financialSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final FinancialAnalysisReportRepository financialReports = mock(FinancialAnalysisReportRepository.class);
    private final MarketResearchRunRepository researchRuns = mock(MarketResearchRunRepository.class);
    private final TwinSurveyRunRepository twinRuns = mock(TwinSurveyRunRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(
        projects, briefs, conceptRuns, portfolioSelections, selections, marketSeeds,
        marketing, marketingSources, professionalReports, financialPreparations,
        financialSnapshots, financialReports, researchRuns, twinRuns);

    @Test
    void launchReadinessIsOptionalAndReadyWithoutUpstreamAnalysis() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));

        var modules = service.findAll(7L, 41L);

        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.TECH_OPS).findFirst().orElseThrow().status())
            .isEqualTo(PipelineModuleStatus.READY);
        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow().status())
            .isEqualTo(PipelineModuleStatus.READY);
    }

    @Test
    void anyCompletedProfessionalReportCompletesTheOptionalLaunchReadinessEntry() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        when(professionalReports.findFirstByProjectIdAndModuleTypeAndDeletedAtIsNullOrderByCompletedAtDesc(
            41L, ProfessionalAnalysisReport.ModuleType.TECHNOLOGY))
            .thenReturn(Optional.of(mock(ProfessionalAnalysisReport.class)));

        var modules = service.findAll(7L, 41L);

        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.TECH_OPS).findFirst().orElseThrow().status())
            .isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow().status())
            .isEqualTo(PipelineModuleStatus.COMPLETED);
    }
}
