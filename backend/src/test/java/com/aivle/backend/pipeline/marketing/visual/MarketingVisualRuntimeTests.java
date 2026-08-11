package com.aivle.backend.pipeline.marketing.visual;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.api.ProjectEvidenceArtifactApiModels;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.marketing.application.MarketingLegalGuard;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.pipeline.marketing.visual.api.MarketingVisualApiModels.CreateRequest;
import com.aivle.backend.pipeline.marketing.visual.api.MarketingVisualController;
import com.aivle.backend.pipeline.marketing.visual.application.*;
import com.aivle.backend.pipeline.marketing.visual.worker.MarketingVisualWorker;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.repository.*;
import com.aivle.backend.taskrun.service.*;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.databind.ObjectMapper;

class MarketingVisualRuntimeTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String hash = "sha256:" + "a".repeat(64);
    private final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");

    @Test
    void productApiCreatesOwnedProjectTaskWithImmutableContentRevisionAndIdempotency() throws Exception {
        ProjectRepository projects = mock(ProjectRepository.class);
        MarketingContentRepository contents = mock(MarketingContentRepository.class);
        MarketingContentRevisionRepository revisions = mock(MarketingContentRevisionRepository.class);
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        MarketingContent content = MarketingContent.queued("content-1", 41L, "source-1", hash,
            "{\"conceptName\":\"상품\"}", "{}", MarketingContentType.BANNER, "social", "title", 7L);
        MarketingContentRevision revision = MarketingContentRevision.create("content-1", 1,
            MarketingRevisionType.USER_EDITED, MarketingRevisionOrigin.USER,
            "{\"contract\":\"marketing-content-result-v1\",\"contentType\":\"BANNER\"}", 7L);
        when(contents.findByIdAndProjectIdAndDeletedAtIsNull("content-1", 41L)).thenReturn(Optional.of(content));
        when(revisions.findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc("content-1")).thenReturn(List.of(revision));
        ProjectEvidenceArtifact artifact = ProjectEvidenceArtifact.create("artifact-1", 41L,
            com.aivle.backend.common.entity.StorageType.LOCAL, "projects/41/source.png", "source.png",
            "source.png", "image/png", 8, "sha256:" + "b".repeat(64), 7L);
        when(artifacts.requireReferenceable(7L, 41L, "artifact-1")).thenReturn(artifact);
        when(hasher.hash(eq(TaskType.MARKETING_VISUAL_GENERATION), eq("1.0"), eq("ko-KR"), anyString())).thenReturn(hash);
        when(taskRuns.create(eq(7L), eq(41L), eq(TaskType.MARKETING_VISUAL_GENERATION),
            eq("MARKETING_VISUAL"), eq("content-1"), anyString(), eq(hash), eq("idem-1"), eq("corr-1"), eq(2)))
            .thenAnswer(invocation -> {
                TaskRun run = mock(TaskRun.class);
                when(run.getId()).thenReturn("task-1"); when(run.getTaskType()).thenReturn(TaskType.MARKETING_VISUAL_GENERATION);
                when(run.getSubjectType()).thenReturn("MARKETING_VISUAL"); when(run.getSubjectId()).thenReturn("content-1");
                when(run.getInputSnapshot()).thenReturn(invocation.getArgument(5)); when(run.getState()).thenReturn(TaskRunState.QUEUED);
                return run;
            });
        MarketingVisualService service = service(projects, contents, revisions, artifacts, taskRuns, hasher);
        CreateRequest request = new CreateRequest(MarketingVisualService.CONTRACT, "content-1", revision.getId(),
            "artifact-1", "행사", "메인", "보조", "밝고 친근한", "가로형 배너", List.of("키워드"));

        var view = service.create(7L, 41L, request, "idem-1", "corr-1");

        assertThat(view.taskRunId()).isEqualTo("task-1");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(taskRuns).create(eq(7L), eq(41L), eq(TaskType.MARKETING_VISUAL_GENERATION),
            eq("MARKETING_VISUAL"), eq("content-1"), input.capture(), eq(hash), eq("idem-1"), eq("corr-1"), eq(2));
        assertThat(input.getValue()).contains(revision.getId(), "artifact-1", "\"source\"", "\"visual\"")
            .doesNotContain("bytesBase64");
        Method create = MarketingVisualController.class.getMethod("create", Long.class, CreateRequest.class,
            String.class, jakarta.servlet.http.HttpServletRequest.class);
        assertThat(create.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void foreignProjectIsDeniedBeforeArtifactOrTaskAccess() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        MarketingVisualService service = service(projects, mock(MarketingContentRepository.class),
            mock(MarketingContentRevisionRepository.class), artifacts, taskRuns, mock(CanonicalInputHasher.class));
        CreateRequest request = new CreateRequest(MarketingVisualService.CONTRACT, "content-1", "revision-1",
            "artifact-1", "행사", "메인", "보조", "밝고 친근한", "가로형 배너", List.of());
        assertThatThrownBy(() -> service.create(7L, 41L, request, "idem", "corr"))
            .isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        verifyNoInteractions(artifacts, taskRuns);
    }

    @Test
    void invalidVisualIntentIsRejectedBeforeMarketingOrArtifactLookup() {
        ProjectRepository projects = mock(ProjectRepository.class);
        MarketingContentRepository contents = mock(MarketingContentRepository.class);
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        MarketingVisualService service = service(projects, contents,
            mock(MarketingContentRevisionRepository.class), artifacts, mock(TaskRunService.class),
            mock(CanonicalInputHasher.class));
        CreateRequest request = new CreateRequest(MarketingVisualService.CONTRACT, "content-1", "revision-1",
            "artifact-1", "행사", "메인", "보조", "지원하지 않는 분위기", "가로형 배너", List.of());

        assertThatThrownBy(() -> service.create(7L, 41L, request, "idem", "corr"))
            .isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(contents, artifacts);
    }

    @Test
    void currentReturnsCanonicalResultAndOwnedArtifactMetadata() {
        ProjectRepository projects = mock(ProjectRepository.class);
        TaskRunRepository runs = mock(TaskRunRepository.class);
        TaskResultRepository results = mock(TaskResultRepository.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        TaskRun run = mock(TaskRun.class);
        when(run.getId()).thenReturn("task-1");
        when(run.getState()).thenReturn(TaskRunState.SUCCEEDED);
        when(run.getInputSnapshot()).thenReturn("{\"marketingContentId\":\"content-1\",\"marketingRevisionId\":\"revision-1\",\"sourceImage\":{\"artifactId\":\"source-1\"}}");
        when(run.getFinalResultId()).thenReturn("result-1");
        when(runs.findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, TaskType.MARKETING_VISUAL_GENERATION, "MARKETING_VISUAL", "content-1"))
            .thenReturn(Optional.of(run));
        TaskResult result = mock(TaskResult.class);
        when(result.getResultJson()).thenReturn("{\"artifact\":{\"artifactId\":\"generated-1\",\"downloadPath\":\"/owned/download\"}}");
        when(results.findById("result-1")).thenReturn(Optional.of(result));
        MarketingVisualService service = new MarketingVisualService(projects,
            mock(MarketingContentRepository.class), mock(MarketingContentRevisionRepository.class),
            mock(ProjectEvidenceArtifactService.class), mock(TaskRunService.class), runs, results,
            mock(CanonicalInputHasher.class), mock(JobEventPublisher.class), mapper);

        var view = service.current(7L, 41L, "content-1");

        assertThat(view.result().path("artifact").path("artifactId").asText()).isEqualTo("generated-1");
        assertThat(view.result().path("artifact").path("downloadPath").asText()).isEqualTo("/owned/download");
        assertThat(view.marketingRevisionId()).isEqualTo("revision-1");
    }

    @Test
    void retryCreatesANewTaskOnlyForRetryableFailure() {
        ProjectRepository projects = mock(ProjectRepository.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
        TaskRun previous = mock(TaskRun.class);
        String immutableInput = "{\"marketingContentId\":\"content-1\",\"marketingRevisionId\":\"revision-1\",\"sourceImage\":{\"artifactId\":\"source-1\"}}";
        when(previous.getTaskType()).thenReturn(TaskType.MARKETING_VISUAL_GENERATION);
        when(previous.getSubjectType()).thenReturn("MARKETING_VISUAL");
        when(previous.getSubjectId()).thenReturn("content-1");
        when(previous.getInputSnapshot()).thenReturn(immutableInput);
        when(previous.terminal()).thenReturn(true);
        when(previous.getState()).thenReturn(TaskRunState.FAILED);
        when(previous.isRetryable()).thenReturn(true);
        when(taskRuns.getOwned(7L, 41L, "failed-1")).thenReturn(previous);
        when(hasher.hash(any(), anyString(), anyString(), anyString())).thenReturn(hash);
        TaskRun replacement = mock(TaskRun.class);
        when(replacement.getId()).thenReturn("task-2");
        when(replacement.getState()).thenReturn(TaskRunState.QUEUED);
        when(replacement.getInputSnapshot()).thenReturn(immutableInput);
        when(taskRuns.create(eq(7L), eq(41L), eq(TaskType.MARKETING_VISUAL_GENERATION),
            eq("MARKETING_VISUAL"), eq("content-1"), anyString(), eq(hash), eq("retry-idem"), eq("corr-2"), eq(2)))
            .thenReturn(replacement);
        MarketingVisualService service = service(projects, mock(MarketingContentRepository.class),
            mock(MarketingContentRevisionRepository.class), mock(ProjectEvidenceArtifactService.class),
            taskRuns, hasher);

        var retried = service.retry(7L, 41L, "failed-1", "retry-idem", "corr-2");

        assertThat(retried.taskRunId()).isEqualTo("task-2");
        verify(taskRuns).create(eq(7L), eq(41L), eq(TaskType.MARKETING_VISUAL_GENERATION),
            eq("MARKETING_VISUAL"), eq("content-1"), eq(immutableInput), eq(hash),
            eq("retry-idem"), eq("corr-2"), eq(2));

        when(previous.isRetryable()).thenReturn(false);
        assertThatThrownBy(() -> service.retry(7L, 41L, "failed-1", "another", "corr-3"))
            .isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.JOB_RETRY_NOT_ALLOWED));
    }

    @Test
    void successfulAiBytesBecomeOwnedArtifactAndCanonicalResultWithoutBase64() {
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        MarketingAssetRepository assets = mock(MarketingAssetRepository.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        when(artifacts.storeGenerated(eq(7L), eq(41L), eq("marketing-visual-task-1.jpg"), eq("image/jpeg"), any()))
            .thenReturn(new ProjectEvidenceArtifactApiModels.ArtifactView("generated-1", 41L,
                "marketing-visual-task-1.jpg", "image/jpeg", 7, "sha256:" + "c".repeat(64), LocalDateTime.now()));
        MarketingVisualCompletionService completion = new MarketingVisualCompletionService(artifacts, assets,
            new MarketingLegalGuard(mapper), taskRuns, mapper);
        ExecutionResponse response = response();

        completion.complete(claim, context(), response);

        verify(taskRuns).assertActiveClaim("task-1", "attempt-1", "token-1");
        verify(taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token-1"),
            argThat(json -> json.contains("generated-1") && json.contains("downloadPath")
                && json.contains("callToAction") && !json.contains("imageBase64")), eq(hash), eq("1.0"));
        ArgumentCaptor<MarketingAsset> linked = ArgumentCaptor.forClass(MarketingAsset.class);
        verify(assets).save(linked.capture());
        assertThat(linked.getValue().getContentId()).isEqualTo("content-1");
        assertThat(linked.getValue().getRevisionId()).isEqualTo("revision-1");
    }

    @Test
    void storageFailureNeverAdoptsTaskAndWorkerMarksRetryableFailure() {
        TaskRunService taskRuns = mock(TaskRunService.class);
        InternalAiExecutionClient ai = mock(InternalAiExecutionClient.class);
        MarketingVisualService visuals = mock(MarketingVisualService.class);
        MarketingVisualCompletionService completion = mock(MarketingVisualCompletionService.class);
        MarketingVisualWorker worker = new MarketingVisualWorker(taskRuns, ai, visuals, completion);
        when(taskRuns.claimNext(eq(TaskType.MARKETING_VISUAL_GENERATION), anyString(), any(), any())).thenReturn(claim);
        when(taskRuns.workerContext("task-1")).thenReturn(context());
        when(visuals.resolveExecutionInput(any())).thenReturn(mapper.createObjectNode());
        ExecutionResponse aiResponse = response();
        when(ai.executeWorkerResolved(any(), anyString(), any(), any())).thenReturn(aiResponse);
        doThrow(new BusinessException(ErrorCode.FILE_STORAGE_FAILED)).when(completion).complete(eq(claim), any(), any());

        assertThat(worker.processOne()).isTrue();

        verify(completion).fail(claim, "EXECUTION_FAILED", "ARTIFACT_STORAGE_FAILED", true);
        verify(visuals).publish(41L, "task-1", "FAILED", "job.marketing.visual.failed",
            com.aivle.backend.jobevent.JobEvent.Status.FAILED, "ARTIFACT_STORAGE_FAILED");
    }

    @Test
    void workerUsesInternalAiAndPublishesRealStagesAndSafeProviderFailure() {
        TaskRunService taskRuns = mock(TaskRunService.class);
        InternalAiExecutionClient ai = mock(InternalAiExecutionClient.class);
        MarketingVisualService visuals = mock(MarketingVisualService.class);
        MarketingVisualCompletionService completion = mock(MarketingVisualCompletionService.class);
        MarketingVisualWorker worker = new MarketingVisualWorker(taskRuns, ai, visuals, completion);
        when(taskRuns.claimNext(eq(TaskType.MARKETING_VISUAL_GENERATION), anyString(), any(), any())).thenReturn(claim);
        when(taskRuns.workerContext("task-1")).thenReturn(context());
        when(visuals.resolveExecutionInput(any())).thenReturn(mapper.createObjectNode());
        when(ai.executeWorkerResolved(any(), anyString(), any(), any()))
            .thenThrow(new ExecutionFailure("EXECUTION_FAILED", "IMAGE_GENERATION_FAILED", true));

        assertThat(worker.processOne()).isTrue();

        verify(completion).fail(claim, "EXECUTION_FAILED", "IMAGE_GENERATION_FAILED", true);
        verify(visuals).publish(41L, "task-1", "INPUT_VALIDATING", "job.marketing.visual.input_validating",
            com.aivle.backend.jobevent.JobEvent.Status.RUNNING, null);
        verify(visuals).publish(41L, "task-1", "VISUAL_GENERATING", "job.marketing.visual.generating",
            com.aivle.backend.jobevent.JobEvent.Status.RUNNING, null);
        verify(visuals).publish(41L, "task-1", "FAILED", "job.marketing.visual.failed",
            com.aivle.backend.jobevent.JobEvent.Status.FAILED, "IMAGE_GENERATION_FAILED");
    }

    private MarketingVisualService service(ProjectRepository projects, MarketingContentRepository contents,
            MarketingContentRevisionRepository revisions, ProjectEvidenceArtifactService artifacts,
            TaskRunService taskRuns, CanonicalInputHasher hasher) {
        return new MarketingVisualService(projects, contents, revisions, artifacts, taskRuns,
            mock(TaskRunRepository.class), mock(TaskResultRepository.class), hasher,
            mock(JobEventPublisher.class), mapper);
    }

    private TaskRunWorkerContext context() {
        return new TaskRunWorkerContext("task-1", 41L, 7L, TaskType.MARKETING_VISUAL_GENERATION,
            "MARKETING_VISUAL", "content-1", "{\"marketingContentId\":\"content-1\",\"marketingRevisionId\":\"revision-1\",\"sourceImage\":{\"artifactId\":\"source-1\"},\"source\":{\"prohibitedClaims\":[],\"requiredDisclosures\":[]},\"visual\":{\"mood\":\"밝고 친근한\",\"bannerFormat\":\"가로형 배너\"}}",
            hash, "idem", "corr", "1.0", "1.0", "ko-KR", 1, 2);
    }

    private ExecutionResponse response() {
        JsonNodeResult result = new JsonNodeResult(mapper.readTree("""
            {"contract":"marketing-visual-generation-result-v1","generatedCopy":{"badge":"행사","headline":"제목","subheadline":"보조"},
             "promptPreview":"prompt","banner":{"imageBase64":"/9j/dmlzdWFs","mediaType":"image/jpeg","model":"gpt-image-2","size":"1536x1024","quality":"high"},
             "legalReview":{"compliant":true,"requiredDisclosuresApplied":[],"requiredControlsApplied":[]}}
            """));
        ExecutionResponse response = mock(ExecutionResponse.class);
        when(response.result()).thenReturn(result.value()); when(response.canonicalInputHash()).thenReturn(hash);
        when(response.resultSchemaVersion()).thenReturn("1.0"); return response;
    }

    private record JsonNodeResult(tools.jackson.databind.JsonNode value) {}
}
