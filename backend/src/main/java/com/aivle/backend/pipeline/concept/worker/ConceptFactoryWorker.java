package com.aivle.backend.pipeline.concept.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.LegalDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.SlotWork;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.Work;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConceptFactoryWorker {
    private final TaskRunService taskRuns;
    private final ConceptFactoryExecutionService execution;
    private final ConceptFactoryAiGateway ai;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;
    private final String workerId = "concept-factory-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${app.task-run.concept-factory-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    @Scheduled(fixedDelayString = "${app.task-run.concept-factory-recovery-interval-ms:5000}")
    public void recover() {
        for (String id : taskRuns.recoverExpiredTaskIds(Duration.ZERO, List.of(TaskType.CONCEPT_FACTORY_RUN))) {
            TaskRunWorkerContext context = taskRuns.workerContext(id);
            publish(context, "QUEUED", "job.concept.run.queued", JobEvent.Status.QUEUED, Map.of(), null);
        }
    }

    public boolean processOne() {
        TaskRunService.Claim claim = taskRuns.claimNext(TaskType.CONCEPT_FACTORY_RUN, workerId, Duration.ofMinutes(10), Duration.ofMinutes(8));
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            Work work = execution.prepare(context.subjectId(), context.projectId());
            boolean slotFailure = processSlots(context, work);
            if (slotFailure) {
                taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "EXECUTION_FAILED", "CONCEPT_SLOT_FAILED", false);
                publish(context, "FAILED", "job.concept.run.failed", JobEvent.Status.FAILED, Map.of(), "CONCEPT_SLOT_FAILED");
            } else if (execution.completeIfEligible(work.runId())) {
                taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{\"status\":\"COMPLETED\"}", context.inputHash(), "1.0");
                publish(context, "COMPLETED", "job.concept.run.completed", JobEvent.Status.COMPLETED, Map.of("eligibleCount", 5), null);
            }
        } catch (RuntimeException failure) {
            log.warn("Concept Factory worker boundary taskRunId={} type={}", claim.taskRunId(), failure.getClass().getSimpleName());
            taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "EXECUTION_FAILED", "INTERNAL_EXECUTION_ERROR", false);
            publish(context, "FAILED", "job.concept.run.failed", JobEvent.Status.FAILED, Map.of(), "INTERNAL_EXECUTION_ERROR");
        }
        return true;
    }

    boolean processSlots(TaskRunWorkerContext context, Work work) {
        boolean slotFailure = false;
        for (SlotWork slot : work.slots()) {
            try {
                processSlot(context, work, slot);
            } catch (PermanentProviderFailure failure) {
                slotFailure = true;
            } catch (RuntimeException isolated) {
                log.warn("Concept slot isolated failure runId={} slot={} type={}", work.runId(), slot.slotNumber(), isolated.getClass().getSimpleName());
                execution.failSlot(work.runId(), slot.slotId(), null, ConceptAttemptError.INTERNAL_EXECUTION_ERROR, false, false);
                slotFailure = true;
            }
        }
        return slotFailure;
    }

    private void processSlot(TaskRunWorkerContext context, Work work, SlotWork slot) {
        ConceptAttemptPhase phase = ConceptAttemptPhase.INITIAL;
        JsonNode candidate = null;
        boolean repaired = false;
        for (int replacement = 0; replacement <= 2; replacement++) {
            String attemptId = execution.beginAttempt(slot.slotId(), phase, context.taskRunId());
            publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.started", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null);
            try {
                if (phase == ConceptAttemptPhase.REDESIGN) {
                    throw new IllegalStateException("redesign requires legal constraints");
                }
                candidate = ai.execute(TaskType.CONCEPT_CANDIDATE, mapper.writeValueAsString(Map.of(
                    "ideaBriefSnapshotId", work.snapshotId(), "variationFocus", slot.focus().name(), "fields", work.fields()
                )), context.correlationId(), attemptId);
            } catch (ExecutionFailure failure) {
                if (failure.retryable()) {
                    execution.recordAttemptError(work.runId(), slot.slotId(), attemptId, ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true);
                    String retryAttempt = execution.beginAttempt(slot.slotId(), phase, context.taskRunId());
                    try {
                        candidate = ai.execute(TaskType.CONCEPT_CANDIDATE, mapper.writeValueAsString(Map.of(
                            "ideaBriefSnapshotId", work.snapshotId(), "variationFocus", slot.focus().name(), "fields", work.fields()
                        )), context.correlationId(), retryAttempt);
                        attemptId = retryAttempt;
                    } catch (ExecutionFailure retryFailure) {
                        execution.recordAttemptError(work.runId(), slot.slotId(), retryAttempt, ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true);
                        execution.beginReplacement(work.runId(), slot.slotId()); phase = ConceptAttemptPhase.REPLACEMENT; continue;
                    }
                } else if ("RESULT_SCHEMA_INVALID".equals(failure.code())) {
                    execution.recordAttemptError(work.runId(), slot.slotId(), attemptId, ConceptAttemptError.SCHEMA_INVALID, false);
                    if (!repaired) {
                        repaired = true; phase = ConceptAttemptPhase.REPAIR; continue;
                    }
                    execution.beginReplacement(work.runId(), slot.slotId()); phase = ConceptAttemptPhase.REPLACEMENT; continue;
                } else {
                    execution.failSlot(work.runId(), slot.slotId(), attemptId, ConceptAttemptError.PERMANENT_PROVIDER_FAILURE, false, true);
                    throw new PermanentProviderFailure();
                }
            }
            execution.generated(slot.slotId(), attemptId, candidate);
            publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.generated", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null);
            JsonNode legal;
            try {
                legal = ai.execute(TaskType.CONCEPT_LEGAL_REVIEW, mapper.writeValueAsString(Map.of(
                    "candidate", candidate, "sharedContext", work.sharedContext()
                )), context.correlationId(), attemptId);
            } catch (ExecutionFailure failure) {
                if (!failure.retryable()) {
                    execution.failSlot(work.runId(), slot.slotId(), attemptId, ConceptAttemptError.PERMANENT_PROVIDER_FAILURE, false, true);
                    throw new PermanentProviderFailure();
                }
                execution.recordAttemptError(work.runId(), slot.slotId(), attemptId, ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true);
                execution.beginReplacement(work.runId(), slot.slotId()); phase = ConceptAttemptPhase.REPLACEMENT; continue;
            }
            publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.validating_legal", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null);
            LegalDisposition disposition = execution.legal(work.runId(), slot.slotId(), attemptId, candidate, legal);
            if (disposition == LegalDisposition.ELIGIBLE) {
                publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.eligible", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null); return;
            }
            if (disposition == LegalDisposition.NEEDS_INPUT) {
                publish(context, "NEEDS_INPUT", "job.concept.run.needs_input", JobEvent.Status.NEEDS_INPUT, Map.of("slot", slot.slotNumber()), null); return;
            }
            if (disposition == LegalDisposition.REPLACE) {
                publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.rejected", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null);
            }
            if (disposition == LegalDisposition.REDESIGN && slot.redesignCount() == 0) {
                publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.redesigning", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null);
                String redesignAttempt = execution.beginAttempt(slot.slotId(), ConceptAttemptPhase.REDESIGN, context.taskRunId());
                candidate = ai.execute(TaskType.CONCEPT_REDESIGN, mapper.writeValueAsString(Map.of(
                    "candidate", candidate, "safeConstraints", legal.path("requiredControls"), "prohibitedVariants", legal.path("prohibitedVariants")
                )), context.correlationId(), redesignAttempt);
                execution.generated(slot.slotId(), redesignAttempt, candidate);
                JsonNode redesignedLegal = ai.execute(TaskType.CONCEPT_LEGAL_REVIEW, mapper.writeValueAsString(Map.of(
                    "candidate", candidate, "sharedContext", work.sharedContext()
                )), context.correlationId(), redesignAttempt);
                if (execution.legal(work.runId(), slot.slotId(), redesignAttempt, candidate, redesignedLegal) == LegalDisposition.ELIGIBLE) return;
            }
            execution.beginReplacement(work.runId(), slot.slotId());
            publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.replacing", JobEvent.Status.RUNNING, Map.of("slot", slot.slotNumber()), null);
            phase = ConceptAttemptPhase.REPLACEMENT;
        }
    }

    private void publish(TaskRunWorkerContext context, String stage, String key, JobEvent.Status status, Map<String, ?> params, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(), context.taskRunId(), stage, key, status, key, params, code));
    }

    private static final class PermanentProviderFailure extends RuntimeException {}
}
