package com.aivle.backend.pipeline.market;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.refinement.ConceptRefinementService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketResearchWorkerAuthorityTests {

    private final MarketResearchService market = mock(MarketResearchService.class);
    private final BusinessValidationCoordinator projection = mock(BusinessValidationCoordinator.class);
    private final ConceptRefinementService refinement = mock(ConceptRefinementService.class);
    private final MarketResearchWorker worker = new MarketResearchWorker(
        mock(TaskRunService.class), mock(InternalAiExecutionClient.class), market, projection,
        refinement, mock(JobEventPublisher.class), new ObjectMapper());

    @Test
    void fullCompletionQueuesOneExactBmWithDeterministicIdempotencyKey() throws Exception {
        TaskRunWorkerContext context = context("market-task-1", "MARKET_RESEARCH_FULL");
        var source = new BusinessValidationCoordinator.MarketChainSource(
            "session-1", 41L, 73L, 5);
        var queued = new MarketResearchService.RunView(
            91L, "BM", "QUEUED", "bm-task-1", "QUEUED", null, false);
        when(projection.marketCompleted("market-task-1")).thenReturn(Optional.of(source));
        when(market.startBmFromVersionAtPlanRevision(7L, 41L, 73L, 5,
            "auto-bm-market-task-1", "auto-bm-market-task-1")).thenReturn(Optional.of(queued));

        invoke("startBusinessModel", context);

        verify(market).startBmFromVersionAtPlanRevision(7L, 41L, 73L, 5,
            "auto-bm-market-task-1", "auto-bm-market-task-1");
        verify(projection).businessModelQueued(
            "session-1", "bm-task-1", "auto-bm-market-task-1");
        verify(refinement, never()).start(7L, 41L,
            "auto-refinement-market-task-1", "auto-refinement-market-task-1");
    }

    @Test
    void bmCompletionQueuesOneRefinementWithDeterministicIdempotencyKey() throws Exception {
        TaskRunWorkerContext context = context("bm-task-1", "MARKET_RESEARCH_BM");
        var source = new BusinessValidationCoordinator.CompletedSource(
            "session-1", 73L, 91L, "seed-1", 31L, 4, 5, "sha256:" + "a".repeat(64));
        when(projection.businessModelCompleted("bm-task-1")).thenReturn(Optional.of(source));

        invoke("startRefinement", context);

        verify(refinement).start(7L, 41L,
            "auto-refinement-bm-task-1", "auto-refinement-bm-task-1");
        verify(projection, never()).marketCompleted("bm-task-1");
    }

    private void invoke(String name, TaskRunWorkerContext context) throws Exception {
        Method method = MarketResearchWorker.class.getDeclaredMethod(name, TaskRunWorkerContext.class);
        method.setAccessible(true);
        method.invoke(worker, context);
    }

    private static TaskRunWorkerContext context(String taskRunId, String subjectType) {
        return new TaskRunWorkerContext(taskRunId, 41L, 7L, TaskType.MARKET_RESEARCH,
            subjectType, "subject-1", "{}", "sha256:" + "b".repeat(64), "key", "corr",
            "1.0", "1.0", "ko-KR", 1, 3);
    }
}
