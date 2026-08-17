package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketing.api.MarketingApiModels;
import com.aivle.backend.pipeline.marketing.application.MarketingContentCompletionService;
import com.aivle.backend.pipeline.marketing.application.MarketingContentService;
import com.aivle.backend.pipeline.marketing.application.MarketingLegalGuard;
import com.aivle.backend.pipeline.marketing.application.MarketingResultContract;
import com.aivle.backend.pipeline.marketing.application.MarketingSourceSnapshotService;
import com.aivle.backend.pipeline.marketing.domain.MarketingAsset;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentRevision;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentType;
import com.aivle.backend.pipeline.marketing.repository.MarketingAssetRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRevisionRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyService;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class MarketingContentArtifactTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void referenceArtifactMustResolveThroughTheOwnedProjectBoundary() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectEvidenceArtifactService evidence = mock(ProjectEvidenceArtifactService.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L))
            .thenReturn(Optional.of(mock(com.aivle.backend.project.entity.Project.class)));
        when(evidence.requireReferenceable(7L, 41L, "00000000-0000-4000-8000-000000000001"))
            .thenThrow(new BusinessException(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND));
        MarketingSourceSnapshotService sources = mock(MarketingSourceSnapshotService.class);
        MarketingContentService service = contentService(projects, evidence, sources);

        assertThatThrownBy(() -> service.create(7L, 41L, request(), "idem", "correlation"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND));
        verify(evidence).requireReferenceable(7L, 41L, "00000000-0000-4000-8000-000000000001");
        verify(sources, never()).requireCurrent(41L);
    }

    @Test
    void referenceArtifactMustBeABoundedPngOrJpeg() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectEvidenceArtifactService evidence = mock(ProjectEvidenceArtifactService.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L))
            .thenReturn(Optional.of(mock(com.aivle.backend.project.entity.Project.class)));
        ProjectEvidenceArtifact artifact = mock(ProjectEvidenceArtifact.class);
        when(artifact.getMediaType()).thenReturn("application/pdf");
        when(artifact.getSizeBytes()).thenReturn(1_024L);
        when(evidence.requireReferenceable(7L, 41L, "00000000-0000-4000-8000-000000000001"))
            .thenReturn(artifact);

        assertThatThrownBy(() -> contentService(projects, evidence,
                mock(MarketingSourceSnapshotService.class)).create(7L, 41L, request(), "idem", "correlation"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MARKETING_ASSET_INVALID));
    }

    @Test
    void completionRejectsAMissingGeneratedArtifactBeforeAssetBinding() {
        CompletionHarness harness = new CompletionHarness();
        when(harness.storage.exists(harness.artifactRef)).thenReturn(false);

        assertThatThrownBy(() -> harness.service.complete(harness.claim, harness.context, harness.response()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing");
        verify(harness.assets, never()).save(any(MarketingAsset.class));
    }

    @Test
    void completionBindsAValidatedGeneratedArtifactToTheGeneratedRevision() throws Exception {
        CompletionHarness harness = new CompletionHarness();
        when(harness.storage.exists(harness.artifactRef)).thenReturn(true);
        when(harness.storage.metadata(harness.artifactRef)).thenReturn(
            new ObjectStoragePort.ObjectMetadata(harness.artifactRef, 12_345L, "image/jpeg"));

        harness.service.complete(harness.claim, harness.context, harness.response());

        ArgumentCaptor<MarketingAsset> linked = ArgumentCaptor.forClass(MarketingAsset.class);
        verify(harness.assets).save(linked.capture());
        assertThat(linked.getValue().getContentId()).isEqualTo("content-1");
        assertThat(linked.getValue().getRevisionId()).isNotBlank();
        assertThat(linked.getValue().getArtifactRef()).isEqualTo(harness.artifactRef);
        assertThat(harness.content.getStatus()).isEqualTo(MarketingContentStatus.COMPLETED);
    }

    private MarketingApiModels.CreateRequest request() {
        return new MarketingApiModels.CreateRequest("marketing-content-request-v1", "source-1",
            MarketingContentType.EMAIL, "email", "launch", "clear", MarketingApiModels.Length.MEDIUM,
            List.of(), List.of(), null, "00000000-0000-4000-8000-000000000001");
    }

    private MarketingContentService contentService(ProjectRepository projects,
            ProjectEvidenceArtifactService evidence, MarketingSourceSnapshotService sources) {
        return new MarketingContentService(projects, evidence, mock(ObjectStoragePort.class),
            mock(CurrentConceptSourceResolver.class), sources, mock(MarketingSourceSnapshotRepository.class),
            mock(MarketingContentRepository.class), mock(MarketingContentRevisionRepository.class),
            mock(MarketingAssetRepository.class), new MarketingResultContract(), new MarketingLegalGuard(mapper),
            mock(MarketingStrategyService.class), mock(TaskRunService.class), mock(CanonicalInputHasher.class),
            mock(JobEventPublisher.class), mapper);
    }

    private final class CompletionHarness {
        private final String artifactRef = "ai-artifacts/00000000-0000-4000-8000-000000000001.jpg";
        private final MarketingContentRepository contents = mock(MarketingContentRepository.class);
        private final MarketingSourceSnapshotRepository sources = mock(MarketingSourceSnapshotRepository.class);
        private final CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
        private final MarketingContentRevisionRepository revisions = mock(MarketingContentRevisionRepository.class);
        private final MarketingAssetRepository assets = mock(MarketingAssetRepository.class);
        private final TaskRunService taskRuns = mock(TaskRunService.class);
        private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
        private final MarketingContent content = MarketingContent.queued("content-1", 41L, "source-1",
            "sha256:" + "a".repeat(64), "{\"prohibitedClaims\":[],\"requiredDisclosures\":[]}", "{}",
            MarketingContentType.EMAIL, "email", "title", 7L, 1, null);
        private final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
        private final TaskRunWorkerContext context = new TaskRunWorkerContext("task-1", 41L, 7L,
            TaskType.MARKETING_CONTENT_GENERATION, "MARKETING_CONTENT", "content-1", "{}",
            "sha256:" + "b".repeat(64), "idem", "correlation", "1.0", "1.0", "ko-KR", 1, 2);
        private final MarketingContentCompletionService service = new MarketingContentCompletionService(
            contents, sources, currentConcepts, revisions, assets, new MarketingResultContract(), new MarketingLegalGuard(mapper),
            taskRuns, storage, mapper);

        private CompletionHarness() {
            content.start();
            MarketingSourceSnapshot source = MarketingSourceSnapshot.createPortfolio("source-1", 41L,
                "market-seed-1", 8L, "concept-1", 2, 3, "2.1",
                "sha256:" + "a".repeat(64), "{}", 7L, java.time.Instant.EPOCH);
            ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
            when(selection.getId()).thenReturn(8L); when(selection.getHypothesisRevision()).thenReturn(2);
            MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
            when(seed.getId()).thenReturn("market-seed-1");
            when(currentConcepts.currentOrNull(41L)).thenReturn(new CurrentConceptSourceResolver.Source(
                selection, seed, new BmPlanPreparationService.PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 3)));
            when(sources.findById("source-1")).thenReturn(Optional.of(source));
            when(contents.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(content));
            when(contents.findLocked("content-1", 41L)).thenReturn(Optional.of(content));
            when(revisions.save(any(MarketingContentRevision.class))).thenAnswer(call -> call.getArgument(0));
            when(assets.save(any(MarketingAsset.class))).thenAnswer(call -> call.getArgument(0));
        }

        private ExecutionResponse response() {
            var result = mapper.readTree("""
                {"contract":"marketing-content-result-v1","contentType":"EMAIL","title":"Hello",
                 "body":"Body","callToAction":null,"hashtags":[],"imageBrief":"제품 이미지",
                 "legalReview":{"compliant":true,"warnings":[],"requiredDisclosuresApplied":[]},
                 "artifactRefs":["%s"]}
                """.formatted(artifactRef));
            return new ExecutionResponse("1.0", "MARKETING_CONTENT_GENERATION", "1.0", "task-1",
                "attempt-1", "correlation", "sha256:" + "b".repeat(64), "1.0", result,
                mapper.createArrayNode(), mapper.createObjectNode(), mapper.createObjectNode());
        }
    }
}
