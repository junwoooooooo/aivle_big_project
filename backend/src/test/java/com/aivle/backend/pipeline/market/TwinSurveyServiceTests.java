package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TwinSurveyServiceTests {
    private static final String INPUT = "{\"contract\":\"twin-panel-survey-input-v1\"}";
    private static final String HASH = "sha256:" + "a".repeat(64);
    @Mock ProjectRepository projects; @Mock CurrentConceptSourceResolver sources;
    @Mock ConceptPortfolioSelectionRepository selections;
    @Mock MarketAnalysisSeedSnapshotRepository seeds; @Mock BmPlanPreparationService bmPlans;
    @Mock TwinSurveyRunRepository runs; @Mock TwinSurveyVersionRepository versions;
    @Mock TaskRunService taskRuns;
    @Mock CanonicalInputHasher hasher; @Mock TwinSurveyInputFactory inputs;
    @Mock Project project; @Mock ConceptPortfolioSelection selection;
    @Mock MarketAnalysisSeedSnapshot seed; @Mock TaskRun task;
    private final ObjectMapper mapper = new ObjectMapper();
    private TwinSurveyService service;
    private CurrentConceptSourceResolver.Source source;

    @BeforeEach void setUp() {
        service = new TwinSurveyService(projects, sources, runs, versions, taskRuns,
            hasher, inputs, mapper);
        lenient().when(project.getId()).thenReturn(41L);
        lenient().when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        lenient().when(selection.getId()).thenReturn(31L);
        lenient().when(selection.getHypothesisRevision()).thenReturn(4);
        lenient().when(seed.getId()).thenReturn("seed-1");
        source = new CurrentConceptSourceResolver.Source(selection, seed,
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3));
        lenient().when(sources.require(eq(41L), anyString())).thenReturn(source);
        lenient().when(sources.currentOrNull(41L)).thenReturn(source);
        lenient().when(inputs.build(eq(source), anyString(), any(), anyInt())).thenReturn(INPUT);
        lenient().when(hasher.hash(TaskType.TWIN_SURVEY, "1.0", "ko-KR", INPUT)).thenReturn(HASH);
        lenient().when(task.getId()).thenReturn("task-1");
        lenient().when(task.getState()).thenReturn(TaskRunState.QUEUED);
        lenient().when(task.getInputSnapshot()).thenReturn(INPUT);
        lenient().when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.TWIN_SURVEY),
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(1)))
            .thenReturn(new TaskRunService.CreateResult(task, true, false));
    }

    @Test void bindsCurrentSeedSelectionRevisionAndBmRevision() {
        var view = service.start(7L, 41L, "상점에서 두 안을 비교합니다.", pairs(), 100, "key", "request");
        assertThat(view.sourceMarketSeedSnapshotId()).isEqualTo("seed-1");
        assertThat(view.sourceSelectionId()).isEqualTo(31L);
        assertThat(view.sourceSelectionRevision()).isEqualTo(4);
        assertThat(view.sourceBmPlanRevision()).isEqualTo(3);
    }

    @Test void currentConceptResolverUsesOnlyTheCurrentNonStaleV2Seed() {
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(seed.getProjectId()).thenReturn(41L);
        when(seed.getSourceType()).thenReturn("CONCEPT_PORTFOLIO_V2");
        when(seeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(31L)).thenReturn(Optional.of(seed));
        when(bmPlans.current(41L)).thenReturn(source.bm());
        var resolved = new CurrentConceptSourceResolver(selections, seeds, bmPlans).currentOrNull(41L);
        assertThat(resolved.seed().getId()).isEqualTo("seed-1");
        assertThat(resolved.selection().getHypothesisRevision()).isEqualTo(4);
        assertThat(resolved.bm().revision()).isEqualTo(3);
    }

    @Test void canonicalSamplesAreAccepted() {
        for (int size : new int[]{50, 100, 300})
            assertThat(service.start(7L, 41L, "상점에서 두 안을 비교합니다.", pairs(), size,
                "key-" + size, "request").sampleSize()).isEqualTo(size);
    }

    @Test void arbitrarySampleIsRejected() {
        assertThatThrownBy(() -> service.start(7L, 41L, "상점에서 두 안을 비교합니다.", pairs(), 80,
            "key", "request")).isInstanceOf(BusinessException.class);
    }

    @Test void sameKeySameInputReplaysDomainRun() {
        TwinSurveyRun existing = run(TwinSurveyRun.State.RUNNING, 1);
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), eq("same"), any(), eq(1))).thenReturn(new TaskRunService.CreateResult(task, false, true));
        when(runs.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(existing));
        assertThat(service.start(7L, 41L, "상점에서 두 안을 비교합니다.", pairs(), 100, "same", "r").attempt())
            .isEqualTo(1);
        verify(runs, never()).save(any());
    }

    @Test void sameKeyChangedInputConflicts() {
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), eq("same"), any(), eq(1))).thenThrow(new TaskRunFailure(
                "IDEMPOTENCY_CONFLICT", "REQUEST_HASH_MISMATCH", HttpStatus.CONFLICT, false));
        assertThatThrownBy(() -> service.start(7L, 41L, "상점에서 두 안을 비교합니다.", pairs(), 100,
            "same", "r")).isInstanceOf(TaskRunFailure.class);
    }

    @Test void sourceOrBmChangeMakesHistoricalRunStale() {
        TwinSurveyRun value = run(TwinSurveyRun.State.SUCCEEDED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(value));
        when(sources.currentOrNull(41L)).thenReturn(new CurrentConceptSourceResolver.Source(selection, seed,
            new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4)));
        assertThat(service.current(7L, 41L).stale()).isTrue();
        assertThat(value.getState()).isEqualTo(TwinSurveyRun.State.STALE);
    }

    @Test void lateResultCannotBecomeCurrent() {
        TwinSurveyRun value = run(TwinSurveyRun.State.RUNNING, 1);
        when(runs.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(value));
        when(versions.findBySourceRunIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        when(versions.countByProjectIdAndDeletedAtIsNull(41L)).thenReturn(0L);
        when(seed.getId()).thenReturn("seed-2");
        var result = mapper.createObjectNode().put("sampleSize", 100).putArray("pairs");
        service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"), response(result));
        assertThat(value.getState()).isEqualTo(TwinSurveyRun.State.STALE);
    }

    @Test void failedRetryUsesSameBoundInputAndIncrementsAttempt() {
        TwinSurveyRun failed = run(TwinSurveyRun.State.FAILED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(failed));
        assertThat(service.retry(7L, 41L, "retry", "request").attempt()).isEqualTo(2);
    }

    @Test void failedRetryAfterSourceChangeIsRejected() {
        TwinSurveyRun failed = run(TwinSurveyRun.State.FAILED, 1);
        when(runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(41L)).thenReturn(Optional.of(failed));
        when(seed.getId()).thenReturn("seed-2");
        assertThatThrownBy(() -> service.retry(7L, 41L, "retry", "request"))
            .isInstanceOf(BusinessException.class);
        assertThat(failed.getState()).isEqualTo(TwinSurveyRun.State.STALE);
    }

    @Test void marketInterviewIsNeitherPrerequisiteNorSurveySource() {
        service.start(7L, 41L, "상점에서 두 안을 비교합니다.", pairs(), 100, "key", "request");
        verify(inputs).build(eq(source), anyString(), any(), eq(100));
    }

    private TwinSurveyRun run(TwinSurveyRun.State state, int attempt) {
        TwinSurveyRun value = TwinSurveyRun.create(project, task, HASH, 100, "seed-1", 31L, 4, 3, attempt);
        if (state == TwinSurveyRun.State.RUNNING) value.running();
        if (state == TwinSurveyRun.State.SUCCEEDED) { value.running(); value.succeed(); }
        if (state == TwinSurveyRun.State.FAILED) value.fail("EXECUTION_FAILED");
        return value;
    }

    private tools.jackson.databind.node.ArrayNode pairs() {
        var values = mapper.createArrayNode();
        values.addObject().put("pairId", "P1");
        return values;
    }

    private ExecutionResponse response(tools.jackson.databind.JsonNode result) {
        return new ExecutionResponse("1.0", "TWIN_SURVEY", "1.0", "task-1", "attempt-1", "request",
            HASH, "1.0", result, mapper.createArrayNode(), mapper.createArrayNode(), null);
    }
}
