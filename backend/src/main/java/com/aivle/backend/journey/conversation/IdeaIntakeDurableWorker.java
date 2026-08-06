package com.aivle.backend.journey.conversation;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class IdeaIntakeDurableWorker {
    private static final List<TaskType> TYPES = List.of(
        TaskType.IDEA_ATTACHMENT_PARSE, TaskType.IDEA_CONVERSATION_TURN);
    private final TaskRunService tasks;
    private final IdeaIntakeClaimService claims;
    private final IdeaAttachmentProcessor attachments;
    private final IdeaAttachmentStateService attachmentState;
    private final IdeaIntakeAiService turns;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;
    private final String workerId = "idea-intake-" + java.util.UUID.randomUUID();

    public IdeaIntakeDurableWorker(TaskRunService tasks, IdeaIntakeClaimService claims,
            IdeaAttachmentProcessor attachments, IdeaAttachmentStateService attachmentState,
            IdeaIntakeAiService turns, JobEventPublisher events, ObjectMapper mapper) {
        this.tasks = tasks;
        this.claims = claims;
        this.attachments = attachments;
        this.attachmentState = attachmentState;
        this.turns = turns;
        this.events = events;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.task-run.idea-intake-poll-interval-ms:1000}")
    public void poll() {
        safeProcess(TaskType.IDEA_ATTACHMENT_PARSE);
        safeProcess(TaskType.IDEA_CONVERSATION_TURN);
    }

    @Scheduled(fixedDelayString = "${app.task-run.idea-intake-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : tasks.recoverExpiredTaskIds(Duration.ZERO, TYPES)) {
            try {
                var run = tasks.workerContext(id);
                publish(run.projectId(), run.taskRunId(), "RECOVERY", "job.recovered",
                    JobEvent.Status.QUEUED, "job.recovered", null);
            } catch (RuntimeException ignored) {
                // Recovery is durable. A later scheduled pass can claim the queued task.
            }
        }
    }

    private void safeProcess(TaskType type) {
        try {
            IdeaIntakeClaimService.ClaimContext context = claims.claimNext(
                type, workerId, Duration.ofMinutes(5), Duration.ofMinutes(3));
            if (context != null) processClaim(context);
        } catch (RuntimeException ignored) {
            // A claimed task is handled inside processClaim; pre-claim failures leave no RUNNING task.
        }
    }

    /** Package-visible for the PostgreSQL transaction-boundary test. */
    void processClaim(IdeaIntakeClaimService.ClaimContext context) {
        TerminalEvent terminal;
        try {
            var run = context.task();
            var claim = context.claim();
            publish(run.projectId(), run.taskRunId(), "WORKER", "job.claimed",
                JobEvent.Status.RUNNING, "job.claimed", null);
            tasks.startExecution(run.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            publish(run.projectId(), run.taskRunId(), "WORKER", "job.started",
                JobEvent.Status.RUNNING, "job.started", null);
            terminal = run.taskType() == TaskType.IDEA_CONVERSATION_TURN
                ? executeConversation(context) : executeAttachment(context);
        } catch (RuntimeException failure) {
            handleFailure(context, failure);
            return;
        }
        try {
            publish(context.task().projectId(), context.task().taskRunId(), terminal.stage(),
                terminal.eventType(), terminal.status(), terminal.messageKey(), null);
        } catch (RuntimeException ignored) {
            // Domain and TaskRun are already committed. Replay/reconciliation must not turn success into failure.
        }
    }

    private TerminalEvent executeConversation(IdeaIntakeClaimService.ClaimContext context) {
        IdeaIntakeAiService.TerminalOutcome outcome = turns.executeClaim(context);
        return new TerminalEvent("FOLLOW_UP_QUESTIONS", "job.completed",
            outcome.needsInput() ? JobEvent.Status.NEEDS_INPUT : JobEvent.Status.COMPLETED,
            outcome.messageKey());
    }

    private TerminalEvent executeAttachment(IdeaIntakeClaimService.ClaimContext context) {
        var run = context.task();
        var claim = context.claim();
        String hash = Boolean.TRUE.equals(context.attachmentAlreadyExtracted())
            ? context.extractedTextHash()
            : attachments.process(run.projectId(), context.conversationId(),
                context.attachmentId(), run.taskRunId());
        String result = mapper.writeValueAsString(Map.of(
            "attachmentId", context.attachmentId(), "extractedTextHash", hash));
        tasks.adopt(run.taskRunId(), claim.taskAttemptId(), claim.claimToken(), result,
            run.inputHash(), "1.0");
        return new TerminalEvent("INFORMATION_EXTRACTION", "job.completed",
            JobEvent.Status.COMPLETED, "job.idea.information.extraction.completed");
    }

    private void handleFailure(IdeaIntakeClaimService.ClaimContext context, RuntimeException failure) {
        var run = context.task();
        var claim = context.claim();
        FailureDecision decision = classify(failure);
        try {
            if (failure instanceof IdeaIntakeAiService.InvalidResultException) {
                tasks.rejectAndFail(run.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                    "{}", "1.0", decision.reason());
            } else {
                tasks.fail(run.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                    decision.code(), decision.reason(), decision.retryable());
            }
            if (decision.retryable() && tasks.scheduleRetry(run.taskRunId(), backoff(run.attemptCount()))) {
                publish(run.projectId(), run.taskRunId(), "RETRY", "job.retry.scheduled",
                    JobEvent.Status.RUNNING, "job.retry.scheduled", null);
                return;
            }
            if (run.taskType() == TaskType.IDEA_ATTACHMENT_PARSE && context.attachmentId() != null) {
                attachmentState.fail(run.projectId(), context.conversationId(), context.attachmentId(),
                    run.taskRunId(), decision.code());
            }
            publish(run.projectId(), run.taskRunId(), "BRIEF_DRAFT", "job.failed",
                JobEvent.Status.FAILED,
                run.taskType() == TaskType.IDEA_CONVERSATION_TURN
                    ? "job.idea.brief.draft.failed" : "job.idea.attachment.parsing.failed",
                decision.code());
        } catch (RuntimeException ignored) {
            // Never let a single claim terminate the scheduler. Lease recovery remains the final safety net.
        }
    }

    private FailureDecision classify(RuntimeException failure) {
        if (failure instanceof ExecutionFailure known) {
            return new FailureDecision(safeCode(known.code()), safeCode(known.reason()), known.retryable());
        }
        if (failure instanceof TaskRunFailure known) {
            return new FailureDecision(safeCode(known.getCode()), safeCode(known.getReason()), known.isRetryable());
        }
        if (failure instanceof TransientDataAccessException) {
            return new FailureDecision("DATABASE_TEMPORARILY_UNAVAILABLE",
                "TRANSIENT_DATABASE_FAILURE", true);
        }
        if (failure instanceof IdeaAttachmentProcessor.AttachmentProcessingException) {
            return new FailureDecision("ATTACHMENT_PARSE_FAILED", "ATTACHMENT_PARSE_FAILED", true);
        }
        if (failure instanceof IdeaIntakeAiService.InvalidResultException) {
            return new FailureDecision("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", false);
        }
        if (failure instanceof IllegalArgumentException) {
            return new FailureDecision("IDEA_INTAKE_CONTRACT_INVALID",
                "IDEA_INTAKE_CONTRACT_INVALID", false);
        }
        return new FailureDecision("IDEA_INTAKE_INTERNAL_FAILURE",
            "IDEA_INTAKE_INTERNAL_FAILURE", false);
    }

    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(30, 1L << Math.max(0, attempt - 1)));
    }

    private String safeCode(String value) {
        return value != null && value.matches("[A-Z0-9._-]{1,80}")
            ? value : "IDEA_INTAKE_INTERNAL_FAILURE";
    }

    private void publish(Long projectId, String taskRunId, String stage, String type,
            JobEvent.Status status, String key, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId,
            stage, type, status, key, Map.of(), code));
    }

    private record TerminalEvent(String stage, String eventType,
                                 JobEvent.Status status, String messageKey) { }
    private record FailureDecision(String code, String reason, boolean retryable) { }
}
