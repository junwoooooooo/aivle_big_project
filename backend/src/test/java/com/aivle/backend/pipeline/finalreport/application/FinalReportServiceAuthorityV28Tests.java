package com.aivle.backend.pipeline.finalreport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.businessvalidation.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Binding;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import com.aivle.backend.pipeline.finalreport.repository.FinalReportSnapshotRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.launchreadiness.repository.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.pipeline.market.*;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.repository.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinalReportServiceAuthorityV28Tests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
    private final BusinessValidationSessionRepository sessions = mock(BusinessValidationSessionRepository.class);
    private final MarketResearchVersionRepository marketVersions = mock(MarketResearchVersionRepository.class);
    private final MarketInterviewRunRepository marketInterviews = mock(MarketInterviewRunRepository.class);
    private final TwinSurveyVersionRepository twinVersions = mock(TwinSurveyVersionRepository.class);
    private final TwinSurveyRunRepository twinRuns = mock(TwinSurveyRunRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final MarketingContentRepository marketingContents = mock(MarketingContentRepository.class);
    private final MarketingContentRevisionRepository marketingRevisions = mock(MarketingContentRevisionRepository.class);
    private final MarketingAssetRepository marketingAssets = mock(MarketingAssetRepository.class);
    private final LaunchReadinessInputSnapshotRepository launchInputs = mock(LaunchReadinessInputSnapshotRepository.class);
    private final LaunchReadinessReportRepository launchReports = mock(LaunchReadinessReportRepository.class);
    private final FinancialInputSnapshotRepository financeSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final TaskRunRepository taskRuns = mock(TaskRunRepository.class);
    private final TaskResultRepository taskResults = mock(TaskResultRepository.class);
    private final ProjectModuleStatusService moduleStatuses = mock(ProjectModuleStatusService.class);
    private final FinalReportSnapshotRepository snapshots = mock(FinalReportSnapshotRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private FinalReportService service;

    @BeforeEach
    void setUp() {
        service = new FinalReportService(projects, currentConcepts, sessions, marketVersions, marketInterviews,
            twinVersions, twinRuns, marketingSources, marketingContents, marketingRevisions, marketingAssets,
            launchInputs, launchReports, financeSnapshots, taskRuns, taskResults, moduleStatuses,
            snapshots, new FinalReportComposer(mapper), mapper);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(41L); when(project.getTitle()).thenReturn("프로젝트");
        when(project.getVersion()).thenReturn(1L); when(project.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 17, 0, 0));
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(moduleStatuses.findAll(7L, 41L)).thenReturn(List.of());
        when(snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(41L)).thenReturn(Optional.empty());
        when(snapshots.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(eq(41L), anyString())).thenReturn(Optional.empty());
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        exactCore();
    }

    @Test
    void exactCompletedBusinessValidationVersionsAreCoreAndOptionalModulesDoNotBlockGeneration() {
        var view = service.generate(7L, 41L, "command-a");

        assertThat(view.state().name()).isEqualTo("CURRENT");
        assertThat(view.blockingSources()).isEmpty();
        assertThat(view.omittedSources()).contains("MARKET_INTERVIEW", "TWIN_SURVEY", "MARKETING");
        verify(marketVersions).findByIdAndProjectIdAndKindAndDeletedAtIsNull(101L, 41L, MarketResearchRun.Kind.FULL);
        verify(marketVersions).findByIdAndProjectIdAndKindAndDeletedAtIsNull(202L, 41L, MarketResearchRun.Kind.BM);
        verify(marketVersions, never()).findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(anyLong(), any());
    }

    @Test
    void sameCommandKeyReplaysAndChangedIdentityConflicts() {
        var first = service.generate(7L, 41L, "command-a");
        ArgumentCaptor<FinalReportSnapshot> saved = ArgumentCaptor.forClass(FinalReportSnapshot.class);
        verify(snapshots).save(saved.capture());
        FinalReportSnapshot stored = saved.getValue();
        when(snapshots.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(41L, "command-a"))
            .thenReturn(Optional.of(stored));

        assertThat(service.generate(7L, 41L, "command-a").snapshotId()).isEqualTo(first.snapshotId());

        FinalReportSnapshot conflict = mock(FinalReportSnapshot.class);
        when(conflict.getCommandIdentityHash()).thenReturn("sha256:" + "f".repeat(64));
        when(snapshots.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(41L, "conflict"))
            .thenReturn(Optional.of(conflict));
        assertThatThrownBy(() -> service.generate(7L, 41L, "conflict"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void missingCurrentConceptIsNotReadyWithoutInventingSources() {
        when(currentConcepts.currentOrNull(41L)).thenReturn(null);
        var view = service.generate(7L, 41L, "command-missing");
        assertThat(view.state().name()).isEqualTo("NOT_READY");
        assertThat(view.blockingSources()).containsExactly("CURRENT_CONCEPT");
        verify(snapshots, never()).save(any());
    }

    private void exactCore() {
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getId()).thenReturn(11L); when(selection.getHypothesisRevision()).thenReturn(4);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(seed.getId()).thenReturn("seed-1"); when(seed.getSnapshotHash()).thenReturn("sha256:" + "1".repeat(64));
        when(seed.getSnapshotJson()).thenReturn("{}"); when(seed.getFinalizedAt()).thenReturn(Instant.parse("2026-08-17T00:00:00Z"));
        Source source = new Source(selection, seed, new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(), mapper.createObjectNode(), 3));
        Binding binding = new Binding("seed-1", 11L, 4, 3);
        when(currentConcepts.currentOrNull(41L)).thenReturn(source); when(currentConcepts.binding(source)).thenReturn(binding);

        BusinessValidationSession session = mock(BusinessValidationSession.class);
        when(session.getId()).thenReturn("bv-1"); when(session.getMarketVersionId()).thenReturn(101L);
        when(session.getBmVersionId()).thenReturn(202L); when(session.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 17, 0, 0));
        when(sessions.findFirstByProjectIdAndSourceMarketSeedSnapshotIdAndSourcePortfolioSelectionIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, "seed-1", 11L, 4, 3, BusinessValidationSession.State.COMPLETED)).thenReturn(Optional.of(session));

        MarketResearchVersion market = version(101L, MarketResearchRun.Kind.FULL);
        MarketResearchVersion bm = version(202L, MarketResearchRun.Kind.BM);
        when(marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(101L, 41L, MarketResearchRun.Kind.FULL)).thenReturn(Optional.of(market));
        when(marketVersions.findByIdAndProjectIdAndKindAndDeletedAtIsNull(202L, 41L, MarketResearchRun.Kind.BM)).thenReturn(Optional.of(bm));
    }

    private MarketResearchVersion version(Long id, MarketResearchRun.Kind kind) {
        MarketResearchVersion value = mock(MarketResearchVersion.class);
        when(value.getId()).thenReturn(id); when(value.getKind()).thenReturn(kind); when(value.getVersionNumber()).thenReturn(1);
        when(value.getResultJson()).thenReturn("{}"); when(value.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 17, 0, 0));
        return value;
    }
}
