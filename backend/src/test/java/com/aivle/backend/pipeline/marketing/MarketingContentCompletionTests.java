package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.market.BmPlanPreparationService.PlanView;
import com.aivle.backend.pipeline.marketing.application.*;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketingContentCompletionTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketingContentRepository contents = mock(MarketingContentRepository.class);
    private final MarketingSourceSnapshotRepository sourceRepository = mock(MarketingSourceSnapshotRepository.class);
    private final CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
    private final MarketingContentRevisionRepository revisions = mock(MarketingContentRevisionRepository.class);
    private final MarketingAssetRepository assets = mock(MarketingAssetRepository.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final MarketingContentCompletionService service = new MarketingContentCompletionService(contents,
        sourceRepository, currentConcepts, revisions, assets, new MarketingResultContract(),
        new MarketingLegalGuard(mapper), taskRuns, mock(ObjectStoragePort.class), mapper);
    private final MarketingSourceSnapshot source = MarketingSourceSnapshot.createPortfolio("source-1", 41L,
        "seed-1", 8L, "concept-1", 6, 4, "2.1", "sha256:" + "a".repeat(64), "{}",
        7L, Instant.EPOCH);
    private MarketingContent content;
    private TaskRunService.Claim claim;
    private TaskRunWorkerContext context;

    @BeforeEach
    void setUp() {
        content = MarketingContent.queued("content-1", 41L, "source-1", source.getSnapshotHash(),
            "{\"prohibitedClaims\":[],\"requiredDisclosures\":[]}", "{}",
            MarketingContentType.EMAIL, "Email", "title", 7L, 1, null);
        content.attachTaskRun("task-1"); content.start();
        claim = new TaskRunService.Claim("task-1", "attempt-1", "token");
        context = new TaskRunWorkerContext("task-1", 41L, 7L, TaskType.MARKETING_CONTENT_GENERATION,
            "MARKETING_EXECUTION", "41", "{}", "sha256:" + "b".repeat(64), "key", "corr",
            "1.0", "1.0", "ko-KR", 1, 1);
        when(contents.findByTaskRunIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(content));
        when(contents.findLocked("content-1", 41L)).thenReturn(Optional.of(content));
        when(sourceRepository.findById("source-1")).thenReturn(Optional.of(source));
        when(revisions.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void lateSuccessIsMaterializedButCannotBecomeCurrentAfterSourceDrift() {
        current("new-seed");

        service.complete(claim, context, response());

        assertThat(content.getStatus()).isEqualTo(MarketingContentStatus.STALE);
        verify(revisions).save(any(MarketingContentRevision.class));
        verify(taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token"), anyString(),
            eq("sha256:" + "b".repeat(64)), eq("1.0"));
    }

    @Test
    void lateFailureIsStaleRatherThanCurrentConceptFailure() {
        current("new-seed");

        service.fail(claim, context, "EXECUTION_FAILED", "DEPENDENCY_RATE_LIMITED", false);

        assertThat(content.getStatus()).isEqualTo(MarketingContentStatus.STALE);
    }

    @Test
    void workerStartPersistsStaleAndDeclinesProviderExecution() {
        current("new-seed");

        assertThat(service.start("task-1", 41L)).isFalse();
        assertThat(content.getStatus()).isEqualTo(MarketingContentStatus.STALE);
    }

    @Test
    void sameSourceFailureRemainsRetryableDomainFailure() {
        current("seed-1");

        service.fail(claim, context, "EXECUTION_FAILED", "DEPENDENCY_RATE_LIMITED", false);

        assertThat(content.getStatus()).isEqualTo(MarketingContentStatus.FAILED);
    }

    private void current(String seedId) {
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getId()).thenReturn(8L); when(selection.getHypothesisRevision()).thenReturn(6);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(seed.getId()).thenReturn(seedId);
        when(currentConcepts.currentOrNull(41L)).thenReturn(new CurrentConceptSourceResolver.Source(selection,
            seed, new PlanView(mapper.createObjectNode(), mapper.createObjectNode(), 4)));
    }

    private ExecutionResponse response() {
        var result = mapper.readTree("""
            {"contract":"marketing-content-result-v1","contentType":"EMAIL","title":"초안",
             "body":"검토할 본문","callToAction":null,"hashtags":[],"imageBrief":null,
             "legalReview":{"compliant":true,"warnings":[],"requiredDisclosuresApplied":[]},
             "artifactRefs":[]}
            """);
        return new ExecutionResponse("1.0", "MARKETING_CONTENT_GENERATION", "1.0", "task-1",
            "attempt-1", "corr", "sha256:" + "b".repeat(64), "1.0", result,
            mapper.createArrayNode(), mapper.createObjectNode(), mapper.createObjectNode());
    }
}
