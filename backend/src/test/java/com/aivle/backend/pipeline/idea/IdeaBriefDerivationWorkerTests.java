package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.idea.application.IdeaBriefDerivationCommitService;
import com.aivle.backend.pipeline.idea.worker.IdeaBriefDerivationWorker;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdeaBriefDerivationWorkerTests {
    @Test
    void publishesNeedsInputOnlyAfterCommitReturnsAlignedTerminalState() {
        TaskRunService tasks = mock(TaskRunService.class);
        InternalAiExecutionClient client = mock(InternalAiExecutionClient.class);
        IdeaBriefDerivationCommitService completion = mock(IdeaBriefDerivationCommitService.class);
        JobEventPublisher events = mock(JobEventPublisher.class);
        TaskRunService.Claim claim = new TaskRunService.Claim("task-needs", "attempt-needs", "claim-needs");
        TaskRunWorkerContext context = context("task-needs");
        when(tasks.claimNext(eq(TaskType.IDEA_BRIEF_DERIVATION), anyString(), any(), any())).thenReturn(claim);
        when(tasks.workerContext("task-needs")).thenReturn(context);
        when(client.executeWorker(eq(context), eq("attempt-needs"), any())).thenReturn(mock(
            InternalAiExecutionClient.ExecutionResponse.class));
        when(completion.complete(eq(claim), eq(context), any()))
            .thenReturn(new IdeaBriefDerivationCommitService.CommitResult("NEEDS_INPUT", 0));

        new IdeaBriefDerivationWorker(tasks, client, completion, events).processOne();

        ArgumentCaptor<JobEventPublisher.Command> published = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(events, atLeastOnce()).publish(published.capture());
        JobEventPublisher.Command terminal = published.getAllValues().get(published.getAllValues().size() - 1);
        assertThat(terminal.status()).isEqualTo(com.aivle.backend.jobevent.JobEvent.Status.NEEDS_INPUT);
        assertThat(terminal.stage()).isEqualTo("NEEDS_INPUT");
    }

    @Test
    void publishesCompletedForReadyForReviewCommit() {
        TaskRunService tasks = mock(TaskRunService.class);
        InternalAiExecutionClient client = mock(InternalAiExecutionClient.class);
        IdeaBriefDerivationCommitService completion = mock(IdeaBriefDerivationCommitService.class);
        JobEventPublisher events = mock(JobEventPublisher.class);
        TaskRunService.Claim claim = new TaskRunService.Claim("task-ready", "attempt-ready", "claim-ready");
        TaskRunWorkerContext context = context("task-ready");
        when(tasks.claimNext(eq(TaskType.IDEA_BRIEF_DERIVATION), anyString(), any(), any())).thenReturn(claim);
        when(tasks.workerContext("task-ready")).thenReturn(context);
        when(client.executeWorker(eq(context), eq("attempt-ready"), any())).thenReturn(mock(
            InternalAiExecutionClient.ExecutionResponse.class));
        when(completion.complete(eq(claim), eq(context), any()))
            .thenReturn(new IdeaBriefDerivationCommitService.CommitResult("READY_FOR_REVIEW", 0));

        new IdeaBriefDerivationWorker(tasks, client, completion, events).processOne();

        ArgumentCaptor<JobEventPublisher.Command> published = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(events, atLeastOnce()).publish(published.capture());
        JobEventPublisher.Command terminal = published.getAllValues().get(published.getAllValues().size() - 1);
        assertThat(terminal.status()).isEqualTo(com.aivle.backend.jobevent.JobEvent.Status.COMPLETED);
        assertThat(terminal.stage()).isEqualTo("SUCCEEDED");
    }

    @Test
    void providerFailureBecomesTerminalTaskBriefAndEventState() {
        TaskRunService tasks = mock(TaskRunService.class);
        InternalAiExecutionClient client = mock(InternalAiExecutionClient.class);
        IdeaBriefDerivationCommitService completion = mock(IdeaBriefDerivationCommitService.class);
        JobEventPublisher events = mock(JobEventPublisher.class);
        TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "claim-1");
        TaskRunWorkerContext context = new TaskRunWorkerContext(
            "task-1", 42L, 7L, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF", "brief-1",
            "{}", "sha256:" + "a".repeat(64), "key", "correlation", "1.0", "1.0", "ko-KR", 1, 3
        );
        when(tasks.claimNext(eq(TaskType.IDEA_BRIEF_DERIVATION), anyString(), any(), any())).thenReturn(claim);
        when(tasks.workerContext("task-1")).thenReturn(context);
        when(client.executeWorker(eq(context), eq("attempt-1"), any()))
            .thenThrow(new ExecutionFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true));

        IdeaBriefDerivationWorker worker = new IdeaBriefDerivationWorker(tasks, client, completion, events);
        worker.processOne();

        verify(tasks).fail("task-1", "attempt-1", "claim-1",
            "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);
        verify(completion).fail("brief-1", 42L);
        verify(events, atLeastOnce()).publish(any());
    }

    private TaskRunWorkerContext context(String taskRunId) {
        return new TaskRunWorkerContext(
            taskRunId, 42L, 7L, TaskType.IDEA_BRIEF_DERIVATION, "IDEA_BRIEF", "brief-1",
            "{}", "sha256:" + "a".repeat(64), "key", "correlation", "1.0", "1.0", "ko-KR", 1, 3
        );
    }
}
