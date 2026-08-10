package com.aivle.backend.pipeline.marketing.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.marketing.application.MarketingContentCompletionService;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketingContentWorkerTests {
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final InternalAiExecutionClient ai = mock(InternalAiExecutionClient.class);
    private final MarketingContentCompletionService completion = mock(MarketingContentCompletionService.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final MarketingContentWorker worker = new MarketingContentWorker(taskRuns, ai, completion, events);
    private final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
    private final TaskRunWorkerContext context = new TaskRunWorkerContext(
        "task-1", 9L, 7L, TaskType.MARKETING_CONTENT_GENERATION, "MARKETING_CONTENT", "content-1",
        "{}", "sha256:" + "0".repeat(64), "key", "correlation", "1.0", "1.0", "ko-KR", 1, 2);

    @Test
    void queuedTaskCompletesWithRealProgressEvents() {
        when(taskRuns.claimNext(eq(TaskType.MARKETING_CONTENT_GENERATION), anyString(), any(), any())).thenReturn(claim);
        when(taskRuns.workerContext("task-1")).thenReturn(context);
        ExecutionResponse response = mock(ExecutionResponse.class);
        when(ai.executeWorker(any(), anyString(), any())).thenReturn(response);

        assertThat(worker.processOne()).isTrue();

        verify(completion).start("content-1", 9L);
        verify(completion).complete(claim, context, response);
        ArgumentCaptor<JobEventPublisher.Command> published = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(events, org.mockito.Mockito.times(5)).publish(published.capture());
        assertThat(published.getAllValues()).extracting(JobEventPublisher.Command::eventType).containsExactly(
            "job.marketing.started", "job.marketing.source_prepared", "job.marketing.copy_generating",
            "job.marketing.legal_checking", "job.marketing.completed");
    }

    @Test
    void providerSchemaFailureTerminatesTaskAndContentTogether() {
        when(taskRuns.claimNext(eq(TaskType.MARKETING_CONTENT_GENERATION), anyString(), any(), any())).thenReturn(claim);
        when(taskRuns.workerContext("task-1")).thenReturn(context);
        when(ai.executeWorker(any(), anyString(), any())).thenThrow(
            new ExecutionFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false));

        assertThat(worker.processOne()).isTrue();

        verify(completion).fail(claim, context, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
        ArgumentCaptor<JobEventPublisher.Command> published = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(events, org.mockito.Mockito.times(4)).publish(published.capture());
        assertThat(published.getAllValues().get(3).status()).isEqualTo(JobEvent.Status.FAILED);
        assertThat(published.getAllValues().get(3).technicalCode()).isEqualTo("AI_RESULT_INVALID");
    }

    @Test
    void prohibitedClaimUsesSafeTerminalCode() {
        when(taskRuns.claimNext(eq(TaskType.MARKETING_CONTENT_GENERATION), anyString(), any(), any())).thenReturn(claim);
        when(taskRuns.workerContext("task-1")).thenReturn(context);
        when(ai.executeWorker(any(), anyString(), any())).thenThrow(
            new ExecutionFailure("EXECUTION_FAILED", "SAFETY_POLICY_BLOCKED", false));

        assertThat(worker.processOne()).isTrue();

        ArgumentCaptor<JobEventPublisher.Command> published = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(events, org.mockito.Mockito.times(4)).publish(published.capture());
        assertThat(published.getAllValues().get(3).technicalCode()).isEqualTo("MARKETING_PROHIBITED_CLAIM");
    }
}
