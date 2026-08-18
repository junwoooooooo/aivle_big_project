package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MarketInterviewServiceTests {
    private static final String HASH = "sha256:" + "a".repeat(64);
    @Mock ProjectRepository projects;
    @Mock CurrentConceptSourceResolver sources;
    @Mock MarketInterviewInputFactory inputs;
    @Mock MarketInterviewRunRepository runs;
    @Mock TaskRunService taskRuns;
    @Mock CanonicalInputHasher hasher;
    @Mock Project project;
    @Mock ConceptPortfolioSelection selection;
    @Mock MarketAnalysisSeedSnapshot seed;
    @Mock TaskRun task;
    private final ObjectMapper mapper = new ObjectMapper();
    private MarketInterviewService service;

    @BeforeEach
    void setUp() {
        service = new MarketInterviewService(projects, sources, inputs, runs,
            taskRuns, hasher, mapper);
        lenient().when(project.getId()).thenReturn(41L);
        lenient().when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        lenient().when(selection.getId()).thenReturn(31L);
        lenient().when(selection.getHypothesisRevision()).thenReturn(4);
        lenient().when(seed.getId()).thenReturn("seed-1");
        lenient().when(seed.getProjectId()).thenReturn(41L);
        lenient().when(seed.getSourceType()).thenReturn("CONCEPT_PORTFOLIO_V2");
        var source = new CurrentConceptSourceResolver.Source(selection, seed,
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3));
        lenient().when(sources.require(eq(41L), anyString())).thenReturn(source);
        lenient().when(sources.currentOrNull(41L)).thenReturn(source);
        lenient().when(inputs.build(any(), any(), any(), anyInt())).thenReturn("{\"contract\":\"market-interview-input-v2\"}");
        lenient().when(hasher.hash(TaskType.MARKET_INTERVIEW, "1.0", "ko-KR", "{\"contract\":\"market-interview-input-v2\"}"))
            .thenReturn(HASH);
        lenient().when(task.getId()).thenReturn("task-1");
        lenient().when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.MARKET_INTERVIEW),
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(1)))
            .thenReturn(new TaskRunService.CreateResult(task, true, false));
    }

    @Test void currentAuthoritativeSeedIsBoundOnlyAfterExplicitStart() {
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.empty());
        assertThat(service.current(7L, 41L).state()).isEqualTo("NOT_STARTED");
        verify(taskRuns, never()).createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(),
            anyString(), anyString(), anyString(), any(), anyInt());
        var started = service.start(7L, 41L, "start-key", "request-1");
        assertThat(started.sourceMarketSeedSnapshotId()).isEqualTo("seed-1");
        assertThat(started.sourceSelectionRevision()).isEqualTo(4);
        assertThat(started.requestedSampleSize()).isEqualTo(20);
    }

    @Test void sampleSizeIsPartOfCanonicalInputAndDurableRun() {
        var started = service.start(7L, 41L, "size-key", "request-1", 80);
        assertThat(started.requestedSampleSize()).isEqualTo(80);
        verify(inputs).build(eq(seed), eq(selection), any(), eq(80));
    }

    @Test void sameKeyAndInputReplaysSameRun() {
        MarketInterviewRun existing = run(MarketInterviewRun.State.RUNNING, 1);
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), eq("same-key"), any(), eq(1))).thenReturn(new TaskRunService.CreateResult(task, false, true));
        when(runs.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(existing));
        assertThat(service.start(7L, 41L, "same-key", "request-1").attempt()).isEqualTo(1);
        verify(runs, never()).save(any());
    }

    @Test void sameKeyWithChangedInputConflictsWithoutDomainRun() {
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), eq("same-key"), any(), eq(1))).thenThrow(new TaskRunFailure(
                "IDEMPOTENCY_CONFLICT", "REQUEST_HASH_MISMATCH", HttpStatus.CONFLICT, false));
        assertThatThrownBy(() -> service.start(7L, 41L, "same-key", "request-2"))
            .isInstanceOf(TaskRunFailure.class);
        verify(runs, never()).save(any());
    }

    @Test void successfulMaterializationStoresExactResultAndAdoptsHash() {
        MarketInterviewRun run = run(MarketInterviewRun.State.RUNNING, 1);
        when(runs.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(run));
        var result = mapper.createObjectNode().put("synthetic", true);
        var response = new ExecutionResponse("1.0", "MARKET_INTERVIEW", "1.0", "task-1", "attempt-1",
            "request-1", HASH, "1.0", result, mapper.createArrayNode(), mapper.createArrayNode(), null);
        service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"), response);
        assertThat(run.getState()).isEqualTo(MarketInterviewRun.State.SUCCEEDED);
        assertThat(mapper.readTree(run.getResultJson()).path("synthetic").asBoolean()).isTrue();
        verify(taskRuns).adopt("task-1", "attempt-1", "claim", result.toString(), HASH, "1.0");
    }

    @Test void sourceChangeMakesCurrentHistoricalAndPreservesRun() {
        MarketInterviewRun run = run(MarketInterviewRun.State.SUCCEEDED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(run));
        when(seed.getId()).thenReturn("seed-2");
        var view = service.current(7L, 41L);
        assertThat(view.state()).isEqualTo("STALE");
        assertThat(view.stale()).isTrue();
        verify(runs, never()).delete(any());
    }

    @Test void lateResultIsStoredAsStaleNotCurrent() {
        MarketInterviewRun run = run(MarketInterviewRun.State.RUNNING, 1);
        when(runs.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(run));
        when(seed.getId()).thenReturn("seed-2");
        var result = mapper.createObjectNode().put("synthetic", true);
        var response = new ExecutionResponse("1.0", "MARKET_INTERVIEW", "1.0", "task-1", "attempt-1",
            "request-1", HASH, "1.0", result, mapper.createArrayNode(), mapper.createArrayNode(), null);
        service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"), response);
        assertThat(run.getState()).isEqualTo(MarketInterviewRun.State.STALE);
        assertThat(run.getResultJson()).isEqualTo(result.toString());
    }

    @Test void failedSameSourceCanRetryWithIncrementedAttempt() {
        MarketInterviewRun failed = run(MarketInterviewRun.State.FAILED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(failed));
        assertThat(service.retry(7L, 41L, "retry-key", "request-2").attempt()).isEqualTo(2);
    }

    @Test void currentFailedRunPublishesCanonicalRetryAndRestartPolicy() {
        when(inputs.preview(any(), any(), any())).thenReturn(mapper.createObjectNode());
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L))
            .thenReturn(Optional.of(run(MarketInterviewRun.State.FAILED, 1)))
            .thenReturn(Optional.of(run(MarketInterviewRun.State.FAILED, MarketInterviewService.MAX_ATTEMPTS)));

        var retryable = service.current(7L, 41L);
        assertThat(retryable.retryAllowed()).isTrue();
        assertThat(retryable.restartAllowed()).isTrue();

        var exhausted = service.current(7L, 41L);
        assertThat(exhausted.retryAllowed()).isFalse();
        assertThat(exhausted.restartAllowed()).isTrue();
    }

    @Test void retryAfterSourceChangeIsRejectedAndOldRunBecomesStale() {
        MarketInterviewRun failed = run(MarketInterviewRun.State.FAILED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(failed));
        when(seed.getId()).thenReturn("seed-2");
        assertThatThrownBy(() -> service.retry(7L, 41L, "retry-key", "request-2"))
            .isInstanceOf(BusinessException.class);
        assertThat(failed.getState()).isEqualTo(MarketInterviewRun.State.STALE);
        verify(taskRuns, never()).createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(),
            anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test void bmRevisionChangeAlsoInvalidatesCurrentRun() {
        MarketInterviewRun run = run(MarketInterviewRun.State.SUCCEEDED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(run));
        when(sources.currentOrNull(41L)).thenReturn(new CurrentConceptSourceResolver.Source(selection, seed,
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4)));
        assertThat(service.current(7L, 41L).stale()).isTrue();
    }

    @Test void failedRunIsPreservedWhenRetryCreatesNewHistoryRow() {
        MarketInterviewRun failed = run(MarketInterviewRun.State.FAILED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(failed));
        service.retry(7L, 41L, "retry-key", "request-2");
        verify(runs, never()).delete(failed);
        assertThat(failed.getState()).isEqualTo(MarketInterviewRun.State.FAILED);
    }

    private MarketInterviewRun run(MarketInterviewRun.State state, int attempt) {
        MarketInterviewRun value = MarketInterviewRun.create(project, task, "seed-1", 31L, 4, 3, 20,
            attempt, "key-" + attempt, HASH, LocalDateTime.now());
        if (state == MarketInterviewRun.State.SUCCEEDED) value.succeed("{\"synthetic\":true}", LocalDateTime.now());
        if (state == MarketInterviewRun.State.FAILED) value.fail("EXECUTION_FAILED", LocalDateTime.now());
        if (state == MarketInterviewRun.State.STALE) value.markStale(null, LocalDateTime.now());
        return value;
    }
}
