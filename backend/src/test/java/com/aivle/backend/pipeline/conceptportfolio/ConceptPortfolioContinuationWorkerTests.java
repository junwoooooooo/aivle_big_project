package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.*;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationMaterializationService;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioContinuationOutcome;
import com.aivle.backend.pipeline.conceptportfolio.worker.*;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ConceptPortfolioContinuationWorkerTests {
    @Test
    void heartbeatsWhileCandidateContinuationAiCallIsBlocking() {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any())).thenAnswer(invocation -> {
            Thread.sleep(450); return harness.response();
        });
        when(harness.materialization.complete(any(), any(), any()))
            .thenReturn(ConceptPortfolioContinuationOutcome.ACCEPTED);
        try {
            assertThat(harness.worker.processOne()).isTrue();
            verify(harness.taskRuns, atLeast(2)).heartbeat("task", "attempt", "token",
                Duration.ofMillis(300));
        } finally { harness.executor.shutdownNow(); }
    }

    @Test
    void authorityLossSuppressesLateMergeAndTerminalEvent() {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any())).thenAnswer(invocation -> {
            Thread.sleep(300); return harness.response();
        });
        doThrow(new IllegalStateException("stale")).when(harness.taskRuns)
            .heartbeat("task", "attempt", "token", Duration.ofMillis(300));
        try {
            assertThat(harness.worker.processOne()).isTrue();
            verify(harness.materialization, never()).complete(any(), any(), any());
            ArgumentCaptor<JobEventPublisher.Command> events =
                ArgumentCaptor.forClass(JobEventPublisher.Command.class);
            verify(harness.publisher, atLeastOnce()).publish(events.capture());
            assertThat(events.getAllValues()).noneMatch(value -> value.status() == JobEvent.Status.COMPLETED
                || value.status() == JobEvent.Status.NEEDS_INPUT || value.status() == JobEvent.Status.FAILED);
        } finally { harness.executor.shutdownNow(); }
    }

    private static final class Harness {
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final InternalAiExecutionClient ai = mock(InternalAiExecutionClient.class);
        final ConceptPortfolioContinuationMaterializationService materialization =
            mock(ConceptPortfolioContinuationMaterializationService.class);
        final JobEventPublisher publisher = mock(JobEventPublisher.class);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final TaskRunService.Claim claim = new TaskRunService.Claim("task", "attempt", "token");
        final TaskRunWorkerContext context = new TaskRunWorkerContext(
            "task", 42L, 7L, TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE,
            "CONCEPT_PORTFOLIO_RUN", "run", "{}", "sha256:" + "a".repeat(64),
            "key", "correlation", "1.0", "1.0", "ko-KR", 1, 2);
        final ConceptPortfolioContinuationWorker worker;

        Harness() {
            when(taskRuns.claimNext(eq(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE), anyString(),
                eq(Duration.ofMillis(300)), eq(Duration.ofSeconds(2)))).thenReturn(claim);
            when(taskRuns.workerContext("task")).thenReturn(context);
            ConceptPortfolioExecutionProperties properties = new ConceptPortfolioExecutionProperties(
                Duration.ofMillis(300), Duration.ofMillis(50), Duration.ofSeconds(2),
                Duration.ofSeconds(1), 1, 1);
            worker = new ConceptPortfolioContinuationWorker(taskRuns, ai, materialization,
                publisher, properties, executor,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        }

        ExecutionResponse response() {
            ObjectMapper mapper = new ObjectMapper();
            return new ExecutionResponse("1.0", "CONCEPT_PORTFOLIO_V2_CONTINUE", "1.0", "task",
                "attempt", "correlation", context.inputHash(), "1.0", mapper.createObjectNode(),
                mapper.createArrayNode(), mapper.createArrayNode(), null);
        }
    }
}
