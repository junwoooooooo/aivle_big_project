package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.market.BmPlanPreparationService.PlanView;
import com.aivle.backend.pipeline.marketing.api.MarketingApiModels;
import com.aivle.backend.pipeline.marketing.application.*;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class MarketingExecutionServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectEvidenceArtifactService evidence = mock(ProjectEvidenceArtifactService.class);
    private final CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
    private final MarketingSourceSnapshotService sourceSnapshots = mock(MarketingSourceSnapshotService.class);
    private final MarketingSourceSnapshotRepository sourceRepository = mock(MarketingSourceSnapshotRepository.class);
    private final MarketingContentRepository contents = mock(MarketingContentRepository.class);
    private final MarketingContentRevisionRepository revisions = mock(MarketingContentRevisionRepository.class);
    private final MarketingAssetRepository assets = mock(MarketingAssetRepository.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final MarketingContentService service = new MarketingContentService(projects, evidence,
        mock(ObjectStoragePort.class), currentConcepts, sourceSnapshots, sourceRepository, contents,
        revisions, assets, new MarketingResultContract(), new MarketingLegalGuard(mapper),
        mock(MarketingStrategyService.class), taskRuns,
        hasher, events, mapper);
    private final ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
    private final MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
    private final PlanView bm = new PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4);
    private final MarketingSourceSnapshot source = MarketingSourceSnapshot.createPortfolio("source-1", 41L,
        "seed-1", 8L, "concept-1", 6, 4, "2.1", "sha256:" + "a".repeat(64),
        "{\"schemaVersion\":\"2.1\",\"conceptName\":\"현재 컨셉\",\"selectionRevision\":6,\"bmPlanRevision\":4}",
        7L, Instant.EPOCH);
    private CurrentConceptSourceResolver.Source authority;
    private TaskRun task;

    @BeforeEach
    void setUp() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        when(selection.getId()).thenReturn(8L); when(selection.getHypothesisRevision()).thenReturn(6);
        when(seed.getId()).thenReturn("seed-1"); when(seed.getProjectId()).thenReturn(41L);
        authority = new CurrentConceptSourceResolver.Source(selection, seed, bm);
        lenient().when(currentConcepts.require(eq(41L), anyString())).thenReturn(authority);
        lenient().when(currentConcepts.currentOrNull(41L)).thenReturn(authority);
        lenient().when(sourceSnapshots.requireCurrent(41L)).thenReturn(source);
        lenient().when(sourceRepository.findById("source-1")).thenReturn(Optional.of(source));
        lenient().when(revisions.findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc(anyString()))
            .thenReturn(List.of());
        lenient().when(assets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(anyString()))
            .thenReturn(List.of());
        lenient().when(contents.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(hasher.hash(eq(TaskType.MARKETING_CONTENT_GENERATION), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn("sha256:" + "b".repeat(64));
        task = mock(TaskRun.class); lenient().when(task.getId()).thenReturn("task-1");
        lenient().when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(new TaskRunService.CreateResult(task, true, false));
    }

    @Test
    void createStoresExactCurrentAuthorityAndCanonicalGenerationOptions() {
        MarketingApiModels.CreateRequest request = request("Instagram", "친근하게");
        var result = service.create(7L, 41L, request, "key-1", "corr-1");

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(taskRuns).createWithDisposition(eq(7L), eq(41L), eq(TaskType.MARKETING_CONTENT_GENERATION),
            eq("MARKETING_EXECUTION"), eq("41"), input.capture(), eq("sha256:" + "b".repeat(64)),
            eq("key-1"), eq("corr-1"), eq(1));
        var json = mapper.readTree(input.getValue());
        assertThat(json.path("source").path("selectionRevision").asInt()).isEqualTo(6);
        assertThat(json.path("source").path("bmPlanRevision").asInt()).isEqualTo(4);
        assertThat(json.path("request").path("channel").asText()).isEqualTo("Instagram");
        assertThat(json.path("request").path("tone").asText()).isEqualTo("친근하게");
        assertThat(json.path("generation").path("designVersion").asText()).isEqualTo("marketing-draft-v1");
        assertThat(result.content().attempt()).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyAndInputReplaysTheExistingGeneration() {
        MarketingContent existing = content(MarketingContentStatus.QUEUED, 1);
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), eq("same-key"), anyString(), eq(1)))
            .thenReturn(new TaskRunService.CreateResult(task, false, true));
        when(contents.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(existing));

        var replay = service.create(7L, 41L, request("Instagram", "친근하게"), "same-key", "corr");

        assertThat(replay.content().contentId()).isEqualTo(existing.getId());
        verify(contents, never()).save(any());
    }

    @Test
    void sameIdempotencyKeyWithChangedInputPreservesConflict() {
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), eq("conflict-key"), anyString(), eq(1)))
            .thenThrow(new TaskRunFailure("IDEMPOTENCY_CONFLICT", "REQUEST_HASH_MISMATCH",
                HttpStatus.CONFLICT, false));
        assertThatThrownBy(() -> service.create(7L, 41L, request("Email", "차분하게"),
            "conflict-key", "corr")).isInstanceOf(TaskRunFailure.class);
        verify(contents, never()).save(any());
    }

    @Test
    void sourceDriftDurablyMarksHistoricalContentStale() {
        MarketingContent old = content(MarketingContentStatus.COMPLETED, 1);
        when(contents.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(41L)).thenReturn(List.of(old));
        when(seed.getId()).thenReturn("new-seed");

        var result = service.list(7L, 41L);

        assertThat(result.contents().get(0).status()).isEqualTo("STALE");
        assertThat(old.getStatus()).isEqualTo(MarketingContentStatus.STALE);
    }

    @Test
    void failedRetryUsesSameSourceAndCreatesANewDurableAttempt() {
        MarketingContent failed = content(MarketingContentStatus.FAILED, 1);
        when(contents.findLocked(failed.getId(), 41L)).thenReturn(Optional.of(failed));

        var retried = service.retry(7L, 41L, failed.getId(), "retry-key", "corr");

        assertThat(retried.content().attempt()).isEqualTo(2);
        assertThat(retried.content().previousContentId()).isEqualTo(failed.getId());
        assertThat(failed.getStatus()).isEqualTo(MarketingContentStatus.FAILED);
    }

    @Test
    void retryRejectsThirdAttemptAndChangedSource() {
        MarketingContent exhausted = content(MarketingContentStatus.FAILED, 3);
        when(contents.findLocked(exhausted.getId(), 41L)).thenReturn(Optional.of(exhausted));
        assertThatThrownBy(() -> service.retry(7L, 41L, exhausted.getId(), "key", "corr"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.JOB_RETRY_NOT_ALLOWED));

        MarketingContent drifted = content(MarketingContentStatus.FAILED, 1);
        when(contents.findLocked(drifted.getId(), 41L)).thenReturn(Optional.of(drifted));
        when(seed.getId()).thenReturn("new-seed");
        var stale = service.retry(7L, 41L, drifted.getId(), "key-2", "corr");
        assertThat(stale.content().status()).isEqualTo("STALE");
        assertThat(drifted.getStatus()).isEqualTo(MarketingContentStatus.STALE);
    }

    @Test
    void staleEditAndFinalizeReturnHistoricalViewWithoutRollingBackOrDeletingHistory() {
        MarketingContent editable = content(MarketingContentStatus.COMPLETED, 1);
        MarketingContent finalizable = content(MarketingContentStatus.COMPLETED, 2);
        when(contents.findLocked(editable.getId(), 41L)).thenReturn(Optional.of(editable));
        when(contents.findLocked(finalizable.getId(), 41L)).thenReturn(Optional.of(finalizable));
        when(seed.getId()).thenReturn("new-seed");

        assertThat(service.edit(7L, 41L, editable.getId(), null).content().status()).isEqualTo("STALE");
        assertThat(service.finalizeContent(7L, 41L, finalizable.getId()).content().status()).isEqualTo("STALE");
        assertThat(editable.getStatus()).isEqualTo(MarketingContentStatus.STALE);
        assertThat(finalizable.getStatus()).isEqualTo(MarketingContentStatus.STALE);
        verify(contents, never()).delete(any());
    }

    @Test
    void regenerateCreatesANewGenerationAndPreservesTheSuccessfulResult() {
        MarketingContent completed = content(MarketingContentStatus.COMPLETED, 1);
        when(contents.findLocked(completed.getId(), 41L)).thenReturn(Optional.of(completed));

        var regenerated = service.regenerate(7L, 41L, completed.getId(), "regen-key", "corr");

        assertThat(regenerated.content().contentId()).isNotEqualTo(completed.getId());
        assertThat(regenerated.content().previousContentId()).isEqualTo(completed.getId());
        assertThat(regenerated.content().attempt()).isEqualTo(1);
        assertThat(completed.getStatus()).isEqualTo(MarketingContentStatus.COMPLETED);
    }

    @Test
    void ownerBoundaryIsCheckedBeforeAnyGenerationMutation() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(8L, 41L, request("Email", "명확하게"), "key", "corr"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        verify(taskRuns, never()).createWithDisposition(eq(8L), anyLong(), any(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    private MarketingContent content(MarketingContentStatus status, int attempt) {
        MarketingContent value = MarketingContent.queued("content-" + status + "-" + attempt, 41L,
            source.getId(), source.getSnapshotHash(), source.getSnapshotJson(),
            mapper.writeValueAsString(request("Instagram", "친근하게")), MarketingContentType.SOCIAL_POST,
            "Instagram", "title", 7L, attempt, null);
        value.attachTaskRun("old-task-" + attempt);
        if (status != MarketingContentStatus.QUEUED) value.start();
        if (status == MarketingContentStatus.COMPLETED) value.completeRevision();
        if (status == MarketingContentStatus.FAILED) value.fail();
        return value;
    }

    private MarketingApiModels.CreateRequest request(String channel, String tone) {
        return new MarketingApiModels.CreateRequest("marketing-content-request-v1", source.getId(),
            MarketingContentType.SOCIAL_POST, channel, "출시 안내", tone,
            MarketingApiModels.Length.MEDIUM, List.of(), List.of(), null, null);
    }
}
