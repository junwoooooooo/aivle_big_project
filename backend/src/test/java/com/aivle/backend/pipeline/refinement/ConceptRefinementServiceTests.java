package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.user.entity.User;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementServiceTests {
    private static final String HASH = "sha256:" + "a".repeat(64);
    @Mock ProjectRepository projects;
    @Mock BusinessValidationCoordinator validations;
    @Mock ConceptPortfolioSelectionRepository selections;
    @Mock MarketAnalysisSeedSnapshotRepository seeds;
    @Mock ConceptRefinementRoundRepository rounds;
    @Mock ConceptRefinementMaterialFactory materials;
    @Mock ConceptPortfolioSelectionTaskFactory tasks;
    @Mock CanonicalInputHasher inputHasher;
    @Mock ConceptRefinementDecisionContract decisions;
    @Mock ConceptRefinementLineageGuard lineage;
    @Mock Project project;
    @Mock User owner;
    @Mock ConceptPortfolioSelection selection;
    @Mock MarketAnalysisSeedSnapshot seed;
    @Mock TaskRun task;
    private final ObjectMapper mapper = new ObjectMapper();
    private ConceptRefinementService service;
    private CompletedSource source;

    @BeforeEach
    void setUp() {
        service = new ConceptRefinementService(projects, validations, selections, seeds, rounds,
            materials, tasks, inputHasher, mapper, decisions, lineage);
        source = new CompletedSource("session-1", 91L, 92L, "seed-1", 31L, 4, 3, HASH);
        when(project.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(7L);
        lenient().when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
        lenient().when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        lenient().when(validations.requireCurrentCompletedSource(7L, 41L)).thenReturn(source);
        lenient().when(selection.getId()).thenReturn(31L);
        lenient().when(selection.getHypothesisRevision()).thenReturn(4);
        lenient().when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L))
            .thenReturn(Optional.of(selection));
        lenient().when(seed.getId()).thenReturn("seed-1");
        lenient().when(seeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(31L))
            .thenReturn(Optional.of(seed));
        lenient().when(materials.input(anyLong(), same(selection), eq(source), anyInt()))
            .thenReturn(mapper.createObjectNode().put("action", "REFINE_FROM_MARKET"));
        lenient().when(inputHasher.hash(any(), eq("1.0"), eq("ko-KR"), anyString())).thenReturn(HASH);
        lenient().when(task.getId()).thenReturn("refine-task-1");
        lenient().when(task.getInputHash()).thenReturn(HASH);
        lenient().when(tasks.create(anyLong(), same(selection), eq("REFINE_FROM_MARKET"),
            any(), anyString(), anyString())).thenReturn(task);
        lenient().when(rounds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(decisions.proposalSet(any())).thenReturn(
            new ConceptRefinementDecisionContract.ProposalSet(HASH, mapper.createArrayNode(),
                Map.of(), List.of()));
    }

    @Test
    void completedCurrentValidationCreatesOneBoundProposingRound() {
        when(rounds.findTopByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberDescIdDesc(
            41L, "session-1")).thenReturn(Optional.empty());
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.empty());

        var view = service.start(7L, 41L, "start-1", "request-1");

        assertThat(view.state()).isEqualTo("PROPOSING");
        ArgumentCaptor<ConceptRefinementRound> saved = ArgumentCaptor.forClass(ConceptRefinementRound.class);
        verify(rounds).save(saved.capture());
        assertThat(saved.getValue().getBusinessValidationSessionId()).isEqualTo("session-1");
        assertThat(saved.getValue().getSourceMarketVersionId()).isEqualTo(91L);
        assertThat(saved.getValue().getSourceBmVersionId()).isEqualTo(92L);
        verify(tasks, times(1)).create(anyLong(), same(selection), eq("REFINE_FROM_MARKET"),
            any(), eq("start-1"), eq("request-1"));
    }

    @Test
    void notCompletedValidationCreatesNoTask() {
        when(validations.requireCurrentCompletedSource(7L, 41L))
            .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> service.start(7L, 41L, "start-1", "request-1"))
            .isInstanceOf(BusinessException.class);
        verify(tasks, never()).create(anyLong(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void staleValidationCreatesNoTask() {
        when(validations.requireCurrentCompletedSource(7L, 41L))
            .thenThrow(new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        assertThatThrownBy(() -> service.start(7L, 41L, "start-1", "request-1"))
            .isInstanceOf(BusinessException.class);
        verify(tasks, never()).create(anyLong(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void staleSeedOrBmMaterialGateCreatesNoTask() {
        when(rounds.findTopByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberDescIdDesc(
            41L, "session-1")).thenReturn(Optional.empty());
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.empty());
        when(materials.input(41L, selection, source, 1))
            .thenThrow(new BusinessException(ErrorCode.MODULE_INPUT_STALE));

        assertThatThrownBy(() -> service.start(7L, 41L, "start-1", "request-1"))
            .isInstanceOf(BusinessException.class);
        verify(tasks, never()).create(anyLong(), any(), anyString(), any(), anyString(), anyString());
        verify(rounds, never()).save(any());
    }

    @Test
    void sameSourceProposingAndAwaitingDecisionNeverDuplicateTask() {
        ConceptRefinementRound round = ConceptRefinementRound.start(41L, source, "old-task", "old-key", HASH);
        when(rounds.findTopByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberDescIdDesc(
            41L, "session-1")).thenReturn(Optional.of(round));

        assertThat(service.start(7L, 41L, "new-key", "request-2").state()).isEqualTo("PROPOSING");
        round.materialize("[{}]", "[]", true);
        assertThat(service.start(7L, 41L, "another-key", "request-3").state())
            .isEqualTo("AWAITING_DECISION");
        verify(tasks, never()).create(anyLong(), any(), anyString(), any(), anyString(), anyString());
        verify(rounds, never()).save(any());
    }

    @Test
    void failedRoundExplicitRetryIncrementsAttempt() {
        ConceptRefinementRound round = ConceptRefinementRound.start(41L, source, "old-task", "start-key", HASH);
        round.fail("AI_SERVICE_UNAVAILABLE");
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(round));
        when(task.getId()).thenReturn("retry-task");

        var view = service.retry(7L, 41L, "retry-key", "request-2");

        assertThat(view.state()).isEqualTo("PROPOSING");
        assertThat(round.getAttempt()).isEqualTo(2);
        assertThat(round.getTaskRunId()).isEqualTo("retry-task");
        verify(materials).input(41L, selection, source, 2);
    }

    @Test
    void retryStopsAfterThreeUserVisibleAttempts() {
        ConceptRefinementRound round = ConceptRefinementRound.start(41L, source, "task-1", "key-1", HASH);
        round.fail("FAILED");
        round.retry("task-2", "key-2", HASH); round.fail("FAILED");
        round.retry("task-3", "key-3", HASH); round.fail("FAILED");
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.retry(7L, 41L, "key-4", "request-4"))
            .isInstanceOf(BusinessException.class);
        verify(tasks, never()).create(anyLong(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void sameKeyWithChangedCanonicalMaterialConflicts() {
        ConceptRefinementRound round = ConceptRefinementRound.start(41L, source, "old-task", "same-key", HASH);
        when(rounds.findTopByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberDescIdDesc(
            41L, "session-1")).thenReturn(Optional.of(round));
        when(inputHasher.hash(any(), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn("sha256:" + "c".repeat(64));

        assertThatThrownBy(() -> service.start(7L, 41L, "same-key", "request-2"))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        verify(tasks, never()).create(anyLong(), any(), anyString(), any(), anyString(), anyString());
    }
}
