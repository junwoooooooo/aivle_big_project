package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.api.TechOpsApiModels.ProposalDecisionRequest;
import com.aivle.backend.pipeline.techops.application.*;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TechOpsServiceAsyncTests {
    @Test
    void currentPortfolioSeedIsAuthoritativeAndDuplicateInitializeReusesPreparation() {
        Harness h = new Harness();
        MarketAnalysisSeedSnapshot source = h.installPortfolioSeed(41L);
        when(h.preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "seed-v2"))
            .thenReturn(Optional.empty())
            .thenAnswer(_ignored -> Optional.of(h.saved));
        when(h.preparations.save(any())).thenAnswer(invocation -> {
            h.saved = invocation.getArgument(0);
            return h.saved;
        });

        var first = h.service.initialize(7L, 41L, "v2-init-1", "request-v2-1");
        var second = h.service.initialize(7L, 41L, "v2-init-2", "request-v2-2");

        assertThat(first.sourceMarketSeedSnapshotId()).isEqualTo("seed-v2");
        assertThat(first.sourceSnapshotHash()).isEqualTo(source.getSnapshotHash());
        assertThat(second.preparationId()).isEqualTo(first.preparationId());
        verify(h.preparations, times(1)).save(any());
        verify(h.selections, never()).findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(anyLong());
    }

    @Test
    void portfolioSelectionWithoutCurrentSeedDoesNotFallBackToLegacy() {
        Harness h = new Harness();
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getId()).thenReturn(77L);
        when(h.portfolioSelections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L))
            .thenReturn(Optional.of(selection));
        when(h.marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(77L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> h.service.initialize(7L, 41L, "v2-missing", "request-v2-missing"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        verify(h.selections, never()).findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(anyLong());
    }

    @Test
    void portfolioSeedFromAnotherProjectIsRejected() {
        Harness h = new Harness();
        h.installPortfolioSeed(99L);

        assertThatThrownBy(() -> h.service.initialize(7L, 41L, "v2-foreign", "request-v2-foreign"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        verify(h.preparations, never()).save(any());
        verify(h.selections, never()).findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(anyLong());
    }

    @Test
    void initializeQueuesOneBatchTaskWithoutCallingProviderAndDuplicateInitializeDoesNotQueueAgain() {
        Harness h = new Harness();
        when(h.preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "seed-1"))
            .thenReturn(Optional.empty())
            .thenAnswer(_ignored -> Optional.of(h.saved));
        when(h.preparations.save(any())).thenAnswer(invocation -> {
            h.saved = invocation.getArgument(0);
            return h.saved;
        });

        var first = h.service.initialize(7L, 41L, "init-command-1", "request-1");
        var second = h.service.initialize(7L, 41L, "init-command-2", "request-2");

        assertThat(first.proposalGenerationStatus()).isEqualTo("QUEUED");
        assertThat(first.activeProposalTaskRunId()).isEqualTo("task-1");
        assertThat(second.preparationId()).isEqualTo(first.preparationId());
        verify(h.taskRuns, times(1)).createWithDisposition(eq(7L), eq(41L),
            eq(TaskType.TECH_OPS_PROPOSAL), eq("TECH_OPS_PREPARATION"), anyString(),
            anyString(), anyString(), eq("init-command-1"), eq("request-1"), eq(1));
    }

    @Test
    void alternativeReturnsQueuedAndDirectEditCanBeatPendingAi() {
        Harness h = new Harness();
        ObjectNode decisions = h.decisions(false);
        ObjectNode delivery = (ObjectNode) decisions.path("deliveryOrProductionMethod");
        delivery.set("proposalValue", h.mapper.readTree("{\"method\":\"온라인 직접 제공\"}"));
        h.saved = TechOpsInputPreparation.create("prep-1", 41L, "seed-1", h.hash,
            "{}", h.mapper.writeValueAsString(decisions), 7L);
        when(h.preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "seed-1"))
            .thenReturn(Optional.of(h.saved));
        when(h.preparations.findLocked("prep-1", 41L)).thenReturn(Optional.of(h.saved));

        var queued = h.service.decideProposal(7L, 41L, "deliveryOrProductionMethod",
            new ProposalDecisionRequest("REJECT_AND_REQUEST_ALTERNATIVE", null),
            "alternative-command-1", "request-a1");
        var edited = h.service.decideProposal(7L, 41L, "deliveryOrProductionMethod",
            new ProposalDecisionRequest("EDIT_AND_ACCEPT", h.mapper.readTree("{\"method\":\"사용자 직접 운영\"}")),
            null, null);

        assertThat(queued.status()).isEqualTo("QUEUED");
        assertThat(queued.taskRunId()).isEqualTo("task-1");
        assertThat(queued.preparation().proposalDecisions().path("deliveryOrProductionMethod")
            .path("proposalValue").path("method").asText()).isEqualTo("온라인 직접 제공");
        assertThat(edited.preparation().proposalDecisions().path("deliveryOrProductionMethod")
            .path("finalValue").path("method").asText()).isEqualTo("사용자 직접 운영");
        assertThat(h.saved.getActiveProposalTaskRunId()).isNull();
    }

    @Test
    void failedInitialGenerationRetriesWithANewTaskRunId() {
        Harness h = new Harness();
        when(h.preparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "seed-1"))
            .thenReturn(Optional.empty())
            .thenAnswer(_ignored -> Optional.of(h.saved));
        when(h.preparations.save(any())).thenAnswer(invocation -> {
            h.saved = invocation.getArgument(0);
            return h.saved;
        });
        h.service.initialize(7L, 41L, "init-command-1", "request-1");
        h.saved.failProposalTask("task-1", h.saved.getProposalDecisionsJson(),
            "FAILED", "AI_SERVICE_UNAVAILABLE");
        when(h.preparations.findLocked(h.saved.getId(), 41L)).thenReturn(Optional.of(h.saved));
        TaskRun retry = mock(TaskRun.class);
        when(retry.getId()).thenReturn("task-2"); when(retry.getState()).thenReturn(TaskRunState.QUEUED);
        when(h.taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.TECH_OPS_PROPOSAL),
            anyString(), anyString(), anyString(), anyString(), eq("retry-command-2"), anyString(), eq(1)))
            .thenReturn(new TaskRunService.CreateResult(retry, true, false));

        var result = h.service.retryInitialProposals(7L, 41L, "retry-command-2", "request-2");

        assertThat(result.taskRunId()).isEqualTo("task-2");
        assertThat(h.saved.getActiveProposalTaskRunId()).isEqualTo("task-2");
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final String hash = "sha256:" + "a".repeat(64);
        final ProjectRepository projects = mock(ProjectRepository.class);
        final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
        final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        final MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        final TechOpsInputPreparationRepository preparations = mock(TechOpsInputPreparationRepository.class);
        final TechOpsEvidenceReferenceRepository evidence = mock(TechOpsEvidenceReferenceRepository.class);
        final ProjectEvidenceArtifactRepository artifacts = mock(ProjectEvidenceArtifactRepository.class);
        final TechOpsInputSnapshotRepository snapshots = mock(TechOpsInputSnapshotRepository.class);
        final TechOpsPreparationFactory factory = mock(TechOpsPreparationFactory.class);
        final TechOpsInputSnapshotFactory snapshotFactory = mock(TechOpsInputSnapshotFactory.class);
        final TechOpsReadiness readiness = mock(TechOpsReadiness.class);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
        final JobEventPublisher events = mock(JobEventPublisher.class);
        final TechOpsService service = new TechOpsService(projects, portfolioSelections, selections, marketSeeds, preparations,
            evidence, artifacts, snapshots, factory, snapshotFactory, readiness, mapper, taskRuns, hasher, events);
        TechOpsInputPreparation saved;

        Harness() {
            Project project = mock(Project.class); User owner = mock(User.class);
            when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
            when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
            ConceptSelection selection = mock(ConceptSelection.class); when(selection.getId()).thenReturn(9L);
            when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L))
                .thenReturn(Optional.of(selection));
            MarketAnalysisSeedSnapshot source = mock(MarketAnalysisSeedSnapshot.class);
            when(source.getId()).thenReturn("seed-1"); when(source.getSnapshotHash()).thenReturn(hash);
            when(source.getSnapshotJson()).thenReturn("{\"contract\":\"market-analysis-seed-v2\"}");
            when(marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(9L, 41L))
                .thenReturn(Optional.of(source));
            when(factory.create(source)).thenReturn(new TechOpsPreparationFactory.InitialPreparation(
                mapper.createObjectNode(), decisions(true)));
            when(snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(anyString(), eq(41L)))
                .thenReturn(Optional.empty());
            when(evidence.findAllByPreparationIdAndDeletedAtIsNullOrderByCreatedAtAsc(anyString()))
                .thenReturn(List.of());
            when(readiness.missing(any(), any())).thenReturn(List.of("deliveryOrProductionMethod"));
            when(hasher.hash(eq(TaskType.TECH_OPS_PROPOSAL), eq("1.0"), eq("ko-KR"), anyString()))
                .thenReturn("sha256:" + "b".repeat(64));
            TaskRun task = mock(TaskRun.class);
            when(task.getId()).thenReturn("task-1"); when(task.getState()).thenReturn(TaskRunState.QUEUED);
            when(task.getIdempotencyKey()).thenReturn("alternative-command-1");
            when(taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.TECH_OPS_PROPOSAL),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(1)))
                .thenReturn(new TaskRunService.CreateResult(task, true, false));
        }

        MarketAnalysisSeedSnapshot installPortfolioSeed(Long sourceProjectId) {
            ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
            when(selection.getId()).thenReturn(77L);
            when(portfolioSelections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L))
                .thenReturn(Optional.of(selection));
            MarketAnalysisSeedSnapshot source = MarketAnalysisSeedSnapshot.createPortfolio("seed-v2", sourceProjectId,
                77L, "concept-v2", "legal-v2", "2.0", hash, hash,
                "{\"contract\":\"market-analysis-seed-v2\"}", 7L, java.time.Instant.EPOCH);
            when(marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(77L))
                .thenReturn(Optional.of(source));
            when(factory.create(source)).thenReturn(new TechOpsPreparationFactory.InitialPreparation(
                mapper.createObjectNode(), decisions(true)));
            return source;
        }

        ObjectNode decisions(boolean allMissing) {
            ObjectNode root = mapper.createObjectNode();
            for (String key : TechOpsPreparationFactory.PROPOSAL_KEYS) {
                ObjectNode field = root.putObject(key);
                field.putNull("proposalValue"); field.putNull("finalValue");
                field.put("source", "AI_HYPOTHESIS"); field.put("decision", "PROPOSED");
                field.put("proposalVersion", 1); field.put("alternativeRequested", false);
                field.putNull("pendingAlternativeTaskRunId");
            }
            return root;
        }
    }
}
