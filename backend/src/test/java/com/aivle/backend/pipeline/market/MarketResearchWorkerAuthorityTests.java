package com.aivle.backend.pipeline.market;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.refinement.ConceptRefinementService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketResearchWorkerAuthorityTests {

    private final MarketResearchService market = mock(MarketResearchService.class);
    private final ConceptRefinementService refinement = mock(ConceptRefinementService.class);
    private final MarketResearchWorker worker = new MarketResearchWorker(
        mock(TaskRunService.class), mock(InternalAiExecutionClient.class), market,
        refinement, mock(JobEventPublisher.class), new ObjectMapper());

    @Test
    void fullCompletionQueuesOneExactBmWithDeterministicIdempotencyKey() throws Exception {
        TaskRunWorkerContext context = context("market-task-1", "MARKET_RESEARCH_FULL");
        var queued = new MarketResearchService.RunView(
            91L, "BM", "QUEUED", "bm-task-1", "QUEUED", null, false);
        when(market.startBm(7L, 41L,
            "auto-bm-market-task-1", "auto-bm-market-task-1")).thenReturn(queued);

        invoke("startBusinessModel", context);

        verify(market).startBm(7L, 41L,
            "auto-bm-market-task-1", "auto-bm-market-task-1");
        verify(refinement, never()).startFirstRoundAfterResearch(41L);
    }

    @Test
    void bmCompletionQueuesOneRefinementWithDeterministicIdempotencyKey() throws Exception {
        TaskRunWorkerContext context = context("bm-task-1", "MARKET_RESEARCH_BM");
        invoke("startRefinement", context);

        verify(refinement).startFirstRoundAfterResearch(41L);
        verify(market, never()).startBm(7L, 41L,
            "auto-bm-bm-task-1", "auto-bm-bm-task-1");
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
