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
import com.aivle.backend.pipeline.marketing.strategy.repository.MarketingStrategyReportRepository;
import com.aivle.backend.pipeline.marketing.strategy.domain.MarketingStrategyReport;
import com.aivle.backend.pipeline.market.*;
import com.aivle.backend.pipeline.marketinterview.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.repository.*;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.jobevent.JobEventPublisher;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

class FinalReportServiceAuthorityV28Tests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
    private final BusinessValidationSessionRepository sessions = mock(BusinessValidationSessionRepository.class);
    private final MarketResearchVersionRepository marketVersions = mock(MarketResearchVersionRepository.class);
    private final MarketInterviewRunRepository marketInterviews = mock(MarketInterviewRunRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final MarketingContentRepository marketingContents = mock(MarketingContentRepository.class);
    private final MarketingContentRevisionRepository marketingRevisions = mock(MarketingContentRevisionRepository.class);
    private final MarketingAssetRepository marketingAssets = mock(MarketingAssetRepository.class);
    private final MarketingStrategyReportRepository marketingStrategies = mock(MarketingStrategyReportRepository.class);
    private final LaunchReadinessInputSnapshotRepository launchInputs = mock(LaunchReadinessInputSnapshotRepository.class);
    private final LaunchReadinessReportRepository launchReports = mock(LaunchReadinessReportRepository.class);
    private final FinancialInputSnapshotRepository financeSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final TaskRunRepository taskRuns = mock(TaskRunRepository.class);
    private final TaskResultRepository taskResults = mock(TaskResultRepository.class);
    private final TaskRunService taskRunService = mock(TaskRunService.class);
    private final CanonicalInputHasher inputHasher = mock(CanonicalInputHasher.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final ProjectModuleStatusService moduleStatuses = mock(ProjectModuleStatusService.class);
    private final FinalReportSnapshotRepository snapshots = mock(FinalReportSnapshotRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private FinalReportService service;

    @BeforeEach
    void setUp() {
        service = new FinalReportService(projects, currentConcepts, sessions, marketVersions, marketInterviews,
            marketingSources, marketingContents, marketingRevisions, marketingAssets,
            marketingStrategies, launchInputs, launchReports, financeSnapshots, taskRuns, taskResults,
            taskRunService, inputHasher, events, moduleStatuses,
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
        assertThat(view.omittedSources()).contains("MARKET_INTERVIEW", "MARKETING");
        assertThat(view.omittedSources()).doesNotContain("TWIN_SURVEY");
        verify(marketVersions, times(2)).findByIdAndProjectIdAndKindAndDeletedAtIsNull(101L, 41L, MarketResearchRun.Kind.FULL);
        verify(marketVersions, times(2)).findByIdAndProjectIdAndKindAndDeletedAtIsNull(202L, 41L, MarketResearchRun.Kind.BM);
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

    @Test
    void currentMarketingStrategyCanBeSelectedAsAnOptionalProposalSource() {
        MarketingStrategyReport strategy = mock(MarketingStrategyReport.class);
        when(strategy.getId()).thenReturn("strategy-1");
        when(strategy.getGeneratedAt()).thenReturn(Instant.parse("2026-08-17T01:00:00Z"));
        when(strategy.getResultJson()).thenReturn("{\"contract\":\"marketing-strategy-result-v1\"}");
        when(strategy.getSourceManifestJson()).thenReturn("""
            {"sources":[
              {"type":"CURRENT_CONCEPT","id":"seed-1"},
              {"type":"MARKET","id":"101"},
              {"type":"BUSINESS_MODEL","id":"202"}
            ]}
            """);
        when(marketingStrategies.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(strategy));

        var view = service.generate(7L, 41L, "strategy-source", List.of("MARKETING_STRATEGY"));

        List<String> types = new java.util.ArrayList<>();
        view.sourceManifest().path("sources").forEach(item -> types.add(item.path("type").asText()));
        assertThat(types).contains("MARKETING_STRATEGY");
    }

    @Test
    void lightweightStatusExposesAvailabilityWithoutReportPayload() {
        var failedInterview = mock(com.aivle.backend.pipeline.marketinterview.MarketInterviewRun.class);
        when(failedInterview.getState()).thenReturn(com.aivle.backend.pipeline.marketinterview.MarketInterviewRun.State.FAILED);
        when(marketInterviews.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(failedInterview));
        var status = service.status(7L, 41L);
        assertThat(status.state()).isEqualTo(com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels.State.READY);
        assertThat(status.availableSources()).contains("CURRENT_CONCEPT", "MARKET", "BUSINESS_MODEL");
        assertThat(status.sourceStates()).doesNotContainKey("TWIN_SURVEY");
        assertThat(status.sourceStates().get("MARKET_INTERVIEW")).isEqualTo("FAILED");
        assertThat(status.currentVersion()).isNull();
    }

    @Test
    void launchSourceUsesLatestIndependentCurrentDocumentInsteadOfConceptBinding() {
        var input = mock(com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.class);
        var report = mock(com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessReport.class);
        when(input.getId()).thenReturn("tech-input"); when(input.isStale()).thenReturn(false);
        when(input.getAttempt()).thenReturn(1);
        when(launchInputs.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType.TECHNOLOGY))
            .thenReturn(Optional.of(input));
        when(launchReports.findFirstByProjectIdAndModuleTypeAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(
            41L, com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType.TECHNOLOGY,
            "tech-input")).thenReturn(Optional.of(report));
        when(report.isCurrent()).thenReturn(true); when(report.isStale()).thenReturn(false);
        when(report.getId()).thenReturn("tech-report"); when(report.getAnalysisJson()).thenReturn("{}");
        when(report.getQualityJson()).thenReturn("{}"); when(report.getExternalEvidenceJson()).thenReturn("{}");
        when(report.getResultHash()).thenReturn("sha256:" + "7".repeat(64));
        when(report.getCompletedAt()).thenReturn(Instant.parse("2026-08-17T02:00:00Z"));

        var view = service.generate(7L, 41L, "launch-independent", List.of("LAUNCH_TECHNOLOGY"));
        assertThat(types(view)).contains("LAUNCH_TECHNOLOGY");
    }

    @Test
    void latestUserDocumentFinanceWithAdoptedReportIsAvailableWithoutConceptBinding() {
        var snapshot = mock(com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot.class);
        var run = mock(com.aivle.backend.taskrun.domain.TaskRun.class);
        var result = mock(com.aivle.backend.taskrun.domain.TaskResult.class);
        when(financeSnapshots.findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, "USER_DOCUMENT_INPUT")).thenReturn(Optional.of(snapshot));
        when(snapshot.getId()).thenReturn("finance-user-doc");
        when(snapshot.getSnapshotHash()).thenReturn("sha256:" + "8".repeat(64));
        when(snapshot.getSnapshotJson()).thenReturn("{}");
        when(snapshot.getFinalizedAt()).thenReturn(Instant.parse("2026-08-17T03:00:00Z"));
        when(run.getId()).thenReturn("finance-report-run");
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, "FINANCIAL_ANALYSIS_REPORT", "finance-user-doc")).thenReturn(Optional.of(run));
        when(result.getValidationState()).thenReturn(com.aivle.backend.taskrun.domain.TaskResultValidationState.ADOPTED);
        when(result.getId()).thenReturn("finance-result"); when(result.getResultHash()).thenReturn("sha256:" + "9".repeat(64));
        when(result.getResultJson()).thenReturn("{}"); when(taskResults.findByTaskRunId("finance-report-run")).thenReturn(List.of(result));

        var view = service.generate(7L, 41L, "finance-independent", List.of("FINANCE"));
        assertThat(types(view)).contains("FINANCE", "FINANCE_REPORT");
    }

    @Test
    void completedMarketingContentIsAvailableAsExplicitDraft() {
        var source = mock(com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot.class);
        var content = mock(com.aivle.backend.pipeline.marketing.domain.MarketingContent.class);
        var revision = mock(com.aivle.backend.pipeline.marketing.domain.MarketingContentRevision.class);
        when(source.getId()).thenReturn("marketing-source"); when(source.getPortfolioSelectionId()).thenReturn(11L);
        when(marketingSources.findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
            "seed-1", 4, 3, 41L)).thenReturn(Optional.of(source));
        when(marketingContents.findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, "marketing-source", com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus.FINALIZED))
            .thenReturn(Optional.empty());
        when(marketingContents.findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            41L, "marketing-source", com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus.COMPLETED))
            .thenReturn(Optional.of(content));
        when(content.getId()).thenReturn("content-1"); when(content.getStatus())
            .thenReturn(com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus.COMPLETED);
        when(content.getCurrentRevisionNumber()).thenReturn(2); when(content.getUpdatedAt())
            .thenReturn(LocalDateTime.of(2026, 8, 17, 4, 0));
        when(marketingRevisions.findByContentIdAndRevisionNumberAndDeletedAtIsNull("content-1", 2))
            .thenReturn(Optional.of(revision));
        when(revision.getId()).thenReturn("revision-2"); when(revision.getRevisionNumber()).thenReturn(2);
        when(revision.getResultJson()).thenReturn("{\"title\":\"초안\"}");

        var view = service.generate(7L, 41L, "marketing-draft", List.of("MARKETING"));
        JsonNode item = find(view.sourceManifest().path("sources"), "MARKETING");
        assertThat(item.path("metadata").path("draft").asBoolean()).isTrue();
        assertThat(service.status(7L, 41L).sourceStates().get("MARKETING")).isEqualTo("AVAILABLE_DRAFT");
    }

    private List<String> types(com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels.FinalReportView view) {
        List<String> result = new java.util.ArrayList<>();
        view.sourceManifest().path("sources").forEach(item -> result.add(item.path("type").asText()));
        return result;
    }

    private tools.jackson.databind.JsonNode find(tools.jackson.databind.JsonNode values, String type) {
        for (var item : values) if (type.equals(item.path("type").asText())) return item;
        return mapper.missingNode();
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
