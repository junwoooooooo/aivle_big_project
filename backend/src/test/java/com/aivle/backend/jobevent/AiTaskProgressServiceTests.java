package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class AiTaskProgressServiceTests {
    private final TaskRunRepository runs = mock(TaskRunRepository.class);
    private final TaskAttemptRepository attempts = mock(TaskAttemptRepository.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final AiTaskProgressService service = new AiTaskProgressService(runs, attempts, events);

    @Test
    void acceptsOnlyCurrentRunningAttemptAndPublishesThroughExistingJobEvent() {
        TaskRun run = run(TaskRunState.RUNNING, "attempt");
        TaskAttempt attempt = mock(TaskAttempt.class);
        when(attempt.getState()).thenReturn(TaskAttemptState.RUNNING);
        when(runs.findById("run")).thenReturn(Optional.of(run));
        when(attempts.findByIdAndTaskRunId("attempt", "run")).thenReturn(Optional.of(attempt));
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.ACCEPTED);
        verify(events).publish(argThat(command -> command.jobId().equals("run")
            && command.messageKey().equals("job.concept-portfolio.trace.drafts-generated")
            && command.messageParams().get("traceSequence").equals(1)
            && command.messageParams().get("traceStage").equals("PLANNING")
            && command.messageParams().get("traceAction").equals("DRAFTS_GENERATED")
            && command.messageParams().get("traceDetail").equals("safe")));
    }

    @Test
    void boundsSafeTraceDetailToPayloadPolicyLimit() {
        TaskRun run = run(TaskRunState.RUNNING, "attempt");
        TaskAttempt attempt = mock(TaskAttempt.class);
        when(attempt.getState()).thenReturn(TaskAttemptState.RUNNING);
        when(runs.findById("run")).thenReturn(Optional.of(run));
        when(attempts.findByIdAndTaskRunId("attempt", "run")).thenReturn(Optional.of(attempt));
        var request = new AiTaskProgressController.ProgressRequest("run", "attempt", "correlation", 1,
            "EXPANDING", "EXPANDED", "PASS", "x".repeat(500), null, null, null, null, Instant.now());

        service.accept(request);

        verify(events).publish(argThat(command ->
            ((String) command.messageParams().get("traceDetail")).length() == 256));
    }

    @Test
    void ignoresStaleAttemptAndLateTerminalProgress() {
        TaskRun current = run(TaskRunState.RUNNING, "new-attempt");
        when(runs.findById("run")).thenReturn(Optional.of(current));
        assertThat(service.accept(request("old-attempt"))).isEqualTo(AiTaskProgressService.Outcome.IGNORED);
        TaskRun terminal = run(TaskRunState.SUCCEEDED, "attempt");
        when(runs.findById("run")).thenReturn(Optional.of(terminal));
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.IGNORED);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsUnknownRunAndMapsActualReasonCodes() {
        when(runs.findById("run")).thenReturn(Optional.empty());
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.NOT_FOUND);
        assertThat(AiTaskProgressService.messageKey("PLAN_VALIDATING", "REJECTED", "DUPLICATE"))
            .isEqualTo("job.concept-portfolio.trace.excluded-duplicate");
    }

    @Test
    void threeTraceEventsArePublishedInSequenceOrder() {
        TaskRun run = run(TaskRunState.RUNNING, "attempt");
        TaskAttempt attempt = mock(TaskAttempt.class);
        when(attempt.getState()).thenReturn(TaskAttemptState.RUNNING);
        when(runs.findById("run")).thenReturn(Optional.of(run));
        when(attempts.findByIdAndTaskRunId("attempt", "run")).thenReturn(Optional.of(attempt));

        for (int sequence = 1; sequence <= 3; sequence++) {
            assertThat(service.accept(request("attempt", sequence)))
                .isEqualTo(AiTaskProgressService.Outcome.ACCEPTED);
        }

        ArgumentCaptor<JobEventPublisher.Command> commands = ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(events, times(3)).publish(commands.capture());
        assertThat(commands.getAllValues().stream()
            .map(command -> (Integer) command.messageParams().get("traceSequence")).toList())
            .containsExactly(1, 2, 3);
    }

    @Test
    void routesAllowlistedMarketAndTwinProgressWithoutChangingCpv2Mapping() {
        TaskAttempt attempt = mock(TaskAttempt.class);
        when(attempt.getState()).thenReturn(TaskAttemptState.RUNNING);
        when(attempts.findByIdAndTaskRunId("attempt", "run")).thenReturn(Optional.of(attempt));

        TaskRun market = run(TaskRunState.RUNNING, "attempt", TaskType.MARKET_RESEARCH,
            "MARKET_RESEARCH_FULL");
        when(runs.findById("run")).thenReturn(Optional.of(market));
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.ACCEPTED);
        verify(events).publish(argThat(command -> command.messageKey().equals("job.market.trace")
            && command.eventType().equals("job.market.trace")));

        reset(events);
        TaskRun businessModel = run(TaskRunState.RUNNING, "attempt", TaskType.MARKET_RESEARCH,
            "MARKET_RESEARCH_BM");
        when(runs.findById("run")).thenReturn(Optional.of(businessModel));
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.ACCEPTED);
        verify(events).publish(argThat(command -> command.messageKey().equals("job.business-model.trace")));

        reset(events);
        TaskRun twin = run(TaskRunState.RUNNING, "attempt", TaskType.TWIN_SURVEY, "TWIN_SURVEY");
        when(runs.findById("run")).thenReturn(Optional.of(twin));
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.ACCEPTED);
        verify(events).publish(argThat(command -> command.messageKey().equals("job.twin.trace")));
    }

    @Test
    void rejectsTaskTypesOutsideProgressAllowlist() {
        TaskRun run = run(TaskRunState.RUNNING, "attempt", TaskType.FINANCE_ESTIMATE, "FINANCE_ESTIMATE");
        when(runs.findById("run")).thenReturn(Optional.of(run));
        assertThat(service.accept(request("attempt"))).isEqualTo(AiTaskProgressService.Outcome.INVALID);
        verifyNoInteractions(events);
    }

    private AiTaskProgressController.ProgressRequest request(String attemptId) {
        return request(attemptId, 1);
    }

    private AiTaskProgressController.ProgressRequest request(String attemptId, int sequence) {
        return new AiTaskProgressController.ProgressRequest("run", attemptId, "correlation", sequence,
            "PLANNING", "DRAFTS_GENERATED", "PASS", "safe", null, null, null, null, Instant.now());
    }

    private TaskRun run(TaskRunState state, String attemptId) {
        return run(state, attemptId, TaskType.CONCEPT_PORTFOLIO_V2_RUN, "CONCEPT_PORTFOLIO_RUN");
    }

    private TaskRun run(TaskRunState state, String attemptId, TaskType taskType, String subjectType) {
        TaskRun run = mock(TaskRun.class);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(42L);
        when(run.getId()).thenReturn("run"); when(run.getProject()).thenReturn(project);
        when(run.getTaskType()).thenReturn(taskType);
        when(run.getSubjectType()).thenReturn(subjectType);
        when(run.getCorrelationId()).thenReturn("correlation");
        when(run.getState()).thenReturn(state); when(run.getCurrentAttemptId()).thenReturn(attemptId);
        when(run.terminal()).thenReturn(state == TaskRunState.SUCCEEDED || state == TaskRunState.FAILED
            || state == TaskRunState.NEEDS_INPUT || state == TaskRunState.CANCELLED || state == TaskRunState.TIMED_OUT);
        return run;
    }
}
