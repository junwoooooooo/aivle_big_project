package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioMaterializationService;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import com.aivle.backend.pipeline.conceptportfolio.worker.*;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ConceptPortfolioWorkerTests {
    @Test
    void heartbeatsRepeatedlyWhileBlockingAiCallRuns() {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any())).thenAnswer(invocation -> {
            Thread.sleep(450);
            return harness.response();
        });
        when(harness.materialization.complete(any(), any(), any()))
            .thenReturn(ConceptPortfolioRunStatus.RESULTS_AVAILABLE);
        try {
            assertThat(harness.worker.processOne()).isTrue();
            verify(harness.taskRuns, atLeast(2)).heartbeat("task", "attempt", "token",
                Duration.ofMillis(300));
            verify(harness.materialization).complete(any(), any(), any());
        } finally { harness.executor.shutdownNow(); }
    }

    @Test
    void heartbeatAuthorityLossCancelsLateResultWithoutMaterializationOrTerminalEvent() {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any())).thenAnswer(invocation -> {
            Thread.sleep(300);
            return harness.response();
        });
        doThrow(new IllegalStateException("stale claim")).when(harness.taskRuns)
            .heartbeat("task", "attempt", "token", Duration.ofMillis(300));
        try {
            assertThat(harness.worker.processOne()).isTrue();
            verify(harness.materialization, never()).complete(any(), any(), any());
            ArgumentCaptor<JobEventPublisher.Command> events = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
            verify(harness.publisher, atLeastOnce()).publish(events.capture());
            assertThat(events.getAllValues()).noneMatch(value -> value.status() == JobEvent.Status.COMPLETED
                || value.status() == JobEvent.Status.NEEDS_INPUT || value.status() == JobEvent.Status.FAILED);
        } finally { harness.executor.shutdownNow(); }
    }

    @Test
    void recoveryRequeuesOnlyExpiredConceptPortfolioTasks() {
        Harness harness = new Harness();
        when(harness.taskRuns.recoverExpiredTaskIds(Duration.ZERO,
            List.of(TaskType.CONCEPT_PORTFOLIO_V2_RUN))).thenReturn(List.of("task"));
        try {
            harness.worker.recover();
            verify(harness.taskRuns).recoverExpiredTaskIds(Duration.ZERO,
                List.of(TaskType.CONCEPT_PORTFOLIO_V2_RUN));
            ArgumentCaptor<JobEventPublisher.Command> event =
                ArgumentCaptor.forClass(JobEventPublisher.Command.class);
            verify(harness.publisher).publish(event.capture());
            assertThat(event.getValue().status()).isEqualTo(JobEvent.Status.QUEUED);
        } finally { harness.executor.shutdownNow(); }
    }

    @Test
    void computesAiDeadlineFromInjectedUtcClock() {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any())).thenReturn(harness.response());
        when(harness.materialization.complete(any(), any(), any()))
            .thenReturn(ConceptPortfolioRunStatus.RESULTS_AVAILABLE);
        try {
            assertThat(harness.worker.processOne()).isTrue();
            ArgumentCaptor<LocalDateTime> deadline = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(harness.ai).executeWorker(any(), eq("attempt"), deadline.capture());
            assertThat(deadline.getValue()).isEqualTo(LocalDateTime.parse("2026-08-10T00:00:01"));
        } finally { harness.executor.shutdownNow(); }
    }

    @ParameterizedTest
    @EnumSource(value = ConceptPortfolioRunStatus.class, names = {
        "RESULTS_AVAILABLE", "RESULTS_WITH_OPEN_INPUT", "NEEDS_INPUT", "FAILED"
    })
    void publishesTerminalJobEventMatchingProductStatus(ConceptPortfolioRunStatus productStatus) {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any())).thenReturn(harness.response());
        when(harness.materialization.complete(any(), any(), any())).thenReturn(productStatus);
        try {
            assertThat(harness.worker.processOne()).isTrue();
            JobEvent.Status expected = switch (productStatus) {
                case RESULTS_AVAILABLE, RESULTS_WITH_OPEN_INPUT -> JobEvent.Status.COMPLETED;
                case NEEDS_INPUT -> JobEvent.Status.NEEDS_INPUT;
                case FAILED -> JobEvent.Status.FAILED;
                default -> throw new IllegalArgumentException("non-terminal test status");
            };
            ArgumentCaptor<JobEventPublisher.Command> events =
                ArgumentCaptor.forClass(JobEventPublisher.Command.class);
            verify(harness.publisher, atLeastOnce()).publish(events.capture());
            assertThat(events.getAllValues()).anyMatch(value -> value.status() == expected);
        } finally { harness.executor.shutdownNow(); }
    }

    @Test
    void preservesSafeFailureReasonAndRetryabilityInTerminalEvent() {
        Harness harness = new Harness();
        when(harness.ai.executeWorker(any(), eq("attempt"), any()))
            .thenThrow(new ExecutionFailure("RATE_LIMITED", "AI_SERVICE_UNAVAILABLE", true));
        try {
            assertThat(harness.worker.processOne()).isTrue();
            ArgumentCaptor<JobEventPublisher.Command> events =
                ArgumentCaptor.forClass(JobEventPublisher.Command.class);
            verify(harness.publisher, atLeastOnce()).publish(events.capture());
            JobEventPublisher.Command failed = events.getAllValues().stream()
                .filter(value -> value.status() == JobEvent.Status.FAILED).findFirst().orElseThrow();
            assertThat(failed.technicalCode()).isEqualTo("RATE_LIMITED");
            assertThat(failed.messageParams().get("failureCode")).isEqualTo("RATE_LIMITED");
            assertThat(failed.messageParams().get("failureReason")).isEqualTo("AI_SERVICE_UNAVAILABLE");
            assertThat(failed.messageParams().get("retryable")).isEqualTo(true);
        } finally { harness.executor.shutdownNow(); }
    }

    @Test
    void preservesBoundedValidationDiagnosticsInTerminalEvent() {
        Harness harness = new Harness();
        var issues = List.of(
            new InternalAiExecutionClient.ValidationIssue("result.continuationArtifacts[0]", "array", "TYPE"),
            new InternalAiExecutionClient.ValidationIssue("result.requiredInputs", "object", "SCHEMA"));
        when(harness.ai.executeWorker(any(), eq("attempt"), any()))
            .thenThrow(new ExecutionFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false,
                issues, 1200L));
        try {
            assertThat(harness.worker.processOne()).isTrue();
            ArgumentCaptor<JobEventPublisher.Command> events =
                ArgumentCaptor.forClass(JobEventPublisher.Command.class);
            verify(harness.publisher, atLeastOnce()).publish(events.capture());
            JobEventPublisher.Command failed = events.getAllValues().stream()
                .filter(value -> value.status() == JobEvent.Status.FAILED).findFirst().orElseThrow();
            assertThat((List<?>) failed.messageParams().get("validationFields")).hasSize(2);
            assertThat(failed.messageParams().get("retryAfterMillis")).isEqualTo(1200L);
        } finally { harness.executor.shutdownNow(); }
    }

    private static final class Harness {
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final InternalAiExecutionClient ai = mock(InternalAiExecutionClient.class);
        final ConceptPortfolioMaterializationService materialization = mock(ConceptPortfolioMaterializationService.class);
        final JobEventPublisher publisher = mock(JobEventPublisher.class);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final TaskRunService.Claim claim = new TaskRunService.Claim("task", "attempt", "token");
        final TaskRunWorkerContext context = new TaskRunWorkerContext(
            "task", 42L, 7L, TaskType.CONCEPT_PORTFOLIO_V2_RUN, "CONCEPT_PORTFOLIO_RUN",
            "portfolio", "{}", "sha256:" + "a".repeat(64), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 2);
        final ConceptPortfolioWorker worker;

        Harness() {
            when(taskRuns.claimNext(eq(TaskType.CONCEPT_PORTFOLIO_V2_RUN), anyString(),
                eq(Duration.ofMillis(300)), eq(Duration.ofSeconds(2)))).thenReturn(claim);
            when(taskRuns.workerContext("task")).thenReturn(context);
            ConceptPortfolioExecutionProperties properties = new ConceptPortfolioExecutionProperties(
                Duration.ofMillis(300), Duration.ofMillis(50), Duration.ofSeconds(2),
                Duration.ofSeconds(1), 1, 1);
            worker = new ConceptPortfolioWorker(taskRuns, ai, materialization, publisher,
                properties, executor,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        }

        ExecutionResponse response() {
            ObjectMapper mapper = new ObjectMapper();
            return new ExecutionResponse("1.0", "CONCEPT_PORTFOLIO_V2_RUN", "1.0", "task",
                "attempt", "correlation", context.inputHash(), "1.0", mapper.createObjectNode(),
                mapper.createArrayNode(), mapper.createArrayNode(), null);
        }
    }
}
