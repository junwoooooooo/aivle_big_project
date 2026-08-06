package com.aivle.backend.journey.boundary;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryBoundaryWorker {
    private static final TaskType TYPE = TaskType.REGULATORY_BOUNDARY_GENERATION;
    private final TaskRunService tasks;
    private final InternalAiExecutionClient client;
    private final RegulatoryBoundaryRunStateService states;
    private final RegulatoryBoundaryCompletionService completion;
    private final RegulatoryBoundaryRunRepository runs;
    private final JobEventPublisher events;
    private final String workerId = "regulatory-boundary-" + UUID.randomUUID();

    public RegulatoryBoundaryWorker(TaskRunService tasks, InternalAiExecutionClient client,
            RegulatoryBoundaryRunStateService states, RegulatoryBoundaryCompletionService completion,
            RegulatoryBoundaryRunRepository runs, JobEventPublisher events) {
        this.tasks = tasks; this.client = client; this.states = states; this.completion = completion;
        this.runs = runs; this.events = events;
    }

    @Scheduled(fixedDelayString = "${app.task-run.regulatory-boundary-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.regulatory-boundary-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : tasks.recoverExpiredTaskIds(Duration.ZERO, List.of(TYPE))) {
            RegulatoryBoundaryRun run = states.requeue(id);
            publish(run, "RECOVERY", "job.boundary.recovered", JobEvent.Status.QUEUED,
                "job.boundary.recovered", null);
        }
    }

    boolean processOne() {
        TaskRunService.Claim claim = tasks.claimNext(TYPE, workerId, Duration.ofMinutes(5), Duration.ofMinutes(3));
        if (claim == null) return false;
        RegulatoryBoundaryRun run = states.start(claim.taskRunId());
        publish(run, "CLASSIFYING", "job.boundary.classification.started", JobEvent.Status.RUNNING,
            "job.boundary.classification.started", null);
        tasks.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        try {
            states.advance(claim.taskRunId(), RegulatoryBoundaryRun.State.ROUTING);
            publish(run, "ROUTING", "job.boundary.routing.completed", JobEvent.Status.RUNNING,
                "job.boundary.routing.completed", null);
            states.advance(claim.taskRunId(), RegulatoryBoundaryRun.State.FETCHING_EVIDENCE);
            publish(run, "FETCHING_EVIDENCE", "job.boundary.evidence.fetch.started", JobEvent.Status.RUNNING,
                "job.boundary.evidence.fetch.started", null);
            TaskRun task = tasks.getOwnedForWorker(claim.taskRunId());
            ExecutionResponse response = client.execute(task, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(3));
            RegulatoryBoundaryContract.validate(response.result());
            publish(run, "FETCHING_EVIDENCE", "job.boundary.evidence.fetch.completed", JobEvent.Status.RUNNING,
                "job.boundary.evidence.fetch.completed", Map.of("evidenceCount", response.result().path("evidence").size()), null);
            states.advance(claim.taskRunId(), RegulatoryBoundaryRun.State.SCREENING);
            publish(run, "SCREENING", "job.boundary.screening.started", JobEvent.Status.RUNNING,
                "job.boundary.screening.started", null);
            states.advance(claim.taskRunId(), RegulatoryBoundaryRun.State.NORMALIZING_RULES);
            publish(run, "NORMALIZING_RULES", "job.boundary.rules.normalizing", JobEvent.Status.RUNNING,
                "job.boundary.rules.normalizing", null);
            states.advance(claim.taskRunId(), RegulatoryBoundaryRun.State.CHECKING_CONFLICTS);
            publish(run, "CHECKING_CONFLICTS", "job.boundary.conflict.checking", JobEvent.Status.RUNNING,
                "job.boundary.conflict.checking", null);
            RegulatoryBoundaryVersion version = completion.complete(claim, response);
            publishTerminal(run, version);
        } catch (ExecutionFailure failure) {
            handleFailure(claim, failure.code(), failure.reason(), failure.retryable());
        } catch (RuntimeException failure) {
            handleFailure(claim, "RESULT_SCHEMA_INVALID", "REGULATORY_BOUNDARY_RESULT_INVALID", false);
        }
        return true;
    }

    private void handleFailure(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        tasks.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
        RegulatoryBoundaryRun run;
        TaskRun task = tasks.getOwnedForWorker(claim.taskRunId());
        if (retryable && tasks.scheduleRetry(claim.taskRunId(), backoff(task.getAttemptCount()))) {
            run = states.requeue(claim.taskRunId());
            publish(run, "RETRY", "job.retry.scheduled", JobEvent.Status.RUNNING, "job.retry.scheduled", null);
        } else {
            run = states.fail(claim.taskRunId(), code);
            publish(run, "FAILED", "job.boundary.failed", JobEvent.Status.FAILED, "job.boundary.failed", code);
        }
    }

    private void publishTerminal(RegulatoryBoundaryRun run, RegulatoryBoundaryVersion version) {
        String status = version.getStatus().name();
        JobEvent.Status eventStatus = switch (version.getStatus()) {
            case READY -> JobEvent.Status.COMPLETED;
            case NEEDS_INPUT -> JobEvent.Status.NEEDS_INPUT;
            case BLOCKED -> JobEvent.Status.BLOCKED;
            default -> JobEvent.Status.FAILED;
        };
        String key = switch (version.getStatus()) {
            case READY -> "job.boundary.completed";
            case NEEDS_INPUT -> "job.boundary.needs_input";
            case BLOCKED -> "job.boundary.blocked";
            default -> "job.boundary.failed";
        };
        publish(run, status, key, eventStatus, key, null);
    }
    private void publish(RegulatoryBoundaryRun run, String stage, String type, JobEvent.Status status,
            String key, String code) { publish(run, stage, type, status, key, Map.of(), code); }
    private void publish(RegulatoryBoundaryRun run, String stage, String type, JobEvent.Status status,
            String key, Map<String, ?> params, String code) {
        events.publish(new JobEventPublisher.Command(run.getProject().getId(), run.getTaskRun().getId(),
            run.getTaskRun().getId(), stage, type, status, key, params, code));
    }
    static Duration backoff(int attempt) { return Duration.ofSeconds(Math.min(30, 1L << Math.max(0, attempt - 1))); }
}
