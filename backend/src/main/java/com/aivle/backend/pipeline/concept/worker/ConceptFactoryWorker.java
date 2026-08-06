package com.aivle.backend.pipeline.concept.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.LegalDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.SlotWork;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.Work;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryLimits;
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
        TaskRunService.Claim claim = taskRuns.claimNext(TaskType.CONCEPT_FACTORY_RUN, workerId,
            Duration.ofMinutes(10), Duration.ofMinutes(8));
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            publish(context, "RUNNING", "job.concept.run.started", JobEvent.Status.RUNNING, Map.of(), null);
            publish(context, "LEGAL_CONTEXT", "job.concept.legal-context.started", JobEvent.Status.RUNNING, Map.of(), null);
            Work work = execution.prepare(context.subjectId(), context.projectId());
            publish(context, "LEGAL_CONTEXT", "job.concept.legal-context.completed", JobEvent.Status.RUNNING, Map.of(), null);
            terminalize(claim, context, work, processSlots(context, work));
        } catch (RuntimeException failure) {
            log.warn("Concept Factory worker boundary taskRunId={} type={}", claim.taskRunId(), failure.getClass().getSimpleName());
            terminalizeFailed(claim, context, context.subjectId(), "INTERNAL_EXECUTION_ERROR");
        }
        return true;
    }

    WorkerOutcome processSlots(TaskRunWorkerContext context, Work work) {
        boolean failed = false;
        for (SlotWork slot : work.slots()) {
            try {
                SlotOutcome outcome = processSlot(context, work, slot);
                if (outcome == SlotOutcome.NEEDS_INPUT) return failed ? WorkerOutcome.FAILED : WorkerOutcome.NEEDS_INPUT;
                if (outcome == SlotOutcome.FAILED) failed = true;
            } catch (RuntimeException isolated) {
                log.warn("Concept slot isolated failure runId={} slot={} type={}", work.runId(), slot.slotNumber(), isolated.getClass().getSimpleName());
                execution.failSlot(work.runId(), slot.slotId(), null, ConceptAttemptError.INTERNAL_EXECUTION_ERROR, false, false);
                failed = true;
            }
        }
        return failed ? WorkerOutcome.FAILED : WorkerOutcome.COMPLETED;
    }

    SlotOutcome processSlot(TaskRunWorkerContext context, Work work, SlotWork slot) {
        for (int replacementRound = 0; replacementRound <= ConceptFactoryLimits.MAX_REPLACEMENT_ROUNDS; replacementRound++) {
            ConceptAttemptPhase phase = replacementRound == 0 ? ConceptAttemptPhase.INITIAL : ConceptAttemptPhase.REPLACEMENT;
            execution.recordCandidateInspection(work.runId());
            Generation generation = generate(context, work, slot, phase, TaskType.CONCEPT_CANDIDATE,
                mapper.writeValueAsString(Map.of("ideaBriefSnapshotId", work.snapshotId(),
                    "variationFocus", slot.focus().name(), "fields", work.fields())));
            if (generation.outcome() == GenerationOutcome.FAILED) return SlotOutcome.FAILED;
            if (generation.outcome() == GenerationOutcome.REPLACE) {
                if (!replace(context, work, slot, replacementRound, generation.attemptId())) return SlotOutcome.FAILED;
                continue;
            }

            JsonNode candidate = generation.result();
            String attemptId = generation.attemptId();
            execution.generated(slot.slotId(), attemptId, candidate);
            publishSlot(context, slot, "job.concept.slot.generated");
            publishSlot(context, slot, "job.concept.slot.validating_origin");
            publishSlot(context, slot, "job.concept.slot.validating_legal");

            Review review = review(context, work, slot, phase, attemptId, candidate);
            if (review.outcome() == ReviewOutcome.FAILED) return SlotOutcome.FAILED;
            if (review.outcome() == ReviewOutcome.REPLACE) {
                publishSlot(context, slot, "job.concept.slot.rejected");
                if (!replace(context, work, slot, replacementRound, review.attemptId())) return SlotOutcome.FAILED;
                continue;
            }
            if (review.disposition() == LegalDisposition.NEEDS_INPUT) return SlotOutcome.NEEDS_INPUT;
            if (review.disposition() == LegalDisposition.ELIGIBLE) {
                publishSlot(context, slot, "job.concept.slot.eligible");
                return SlotOutcome.ELIGIBLE;
            }

            if (review.disposition() == LegalDisposition.REDESIGN && slot.redesignCount() == 0) {
                publishSlot(context, slot, "job.concept.slot.redesigning");
                Generation redesign = generate(context, work, slot, ConceptAttemptPhase.REDESIGN, TaskType.CONCEPT_REDESIGN,
                    mapper.writeValueAsString(Map.of("candidate", candidate,
                        "safeConstraints", review.legal().path("requiredControls"),
                        "prohibitedVariants", review.legal().path("prohibitedVariants"))));
                if (redesign.outcome() == GenerationOutcome.FAILED) return SlotOutcome.FAILED;
                attemptId = redesign.attemptId();
                if (redesign.outcome() == GenerationOutcome.SUCCESS) {
                    execution.generated(slot.slotId(), redesign.attemptId(), redesign.result());
                    publishSlot(context, slot, "job.concept.slot.generated");
                    publishSlot(context, slot, "job.concept.slot.validating_origin");
                    publishSlot(context, slot, "job.concept.slot.validating_legal");
                    Review redesigned = review(context, work, slot, ConceptAttemptPhase.REDESIGN,
                        redesign.attemptId(), redesign.result());
                    if (redesigned.outcome() == ReviewOutcome.FAILED) return SlotOutcome.FAILED;
                    if (redesigned.disposition() == LegalDisposition.NEEDS_INPUT) return SlotOutcome.NEEDS_INPUT;
                    if (redesigned.disposition() == LegalDisposition.ELIGIBLE) {
                        publishSlot(context, slot, "job.concept.slot.eligible");
                        return SlotOutcome.ELIGIBLE;
                    }
                }
            }

            publishSlot(context, slot, "job.concept.slot.rejected");
            if (!replace(context, work, slot, replacementRound, attemptId)) return SlotOutcome.FAILED;
        }
        throw new IllegalStateException("bounded replacement loop did not terminate");
    }

    private Generation generate(TaskRunWorkerContext context, Work work, SlotWork slot, ConceptAttemptPhase phase,
            TaskType type, String input) {
        String attemptId = execution.beginAttempt(slot.slotId(), phase, context.taskRunId());
        publishSlot(context, slot, "job.concept.slot.started");
        AiCall call = callWithTransientRetry(context, work, slot, phase, type, input, attemptId);
        if (call.result() != null) return new Generation(GenerationOutcome.SUCCESS, call.attemptId(), call.result());
        if (isSchema(call.failure())) {
            execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), ConceptAttemptError.SCHEMA_INVALID, false);
            String repairAttempt = execution.beginAttempt(slot.slotId(), ConceptAttemptPhase.REPAIR, context.taskRunId());
            AiCall repair = callWithTransientRetry(context, work, slot, ConceptAttemptPhase.REPAIR, type, input, repairAttempt);
            if (repair.result() != null) return new Generation(GenerationOutcome.SUCCESS, repair.attemptId(), repair.result());
            return generationFailure(work, slot, repair);
        }
        return generationFailure(work, slot, call);
    }

    private Generation generationFailure(Work work, SlotWork slot, AiCall call) {
        ConceptAttemptError classification = classify(call.failure());
        if (classification == ConceptAttemptError.PERMANENT_PROVIDER_FAILURE) {
            execution.failSlot(work.runId(), slot.slotId(), call.attemptId(), classification, false, true);
            return new Generation(GenerationOutcome.FAILED, call.attemptId(), null);
        }
        execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), classification,
            classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE);
        return new Generation(GenerationOutcome.REPLACE, call.attemptId(), null);
    }

    private Review review(TaskRunWorkerContext context, Work work, SlotWork slot, ConceptAttemptPhase phase,
            String attemptId, JsonNode candidate) {
        String input = mapper.writeValueAsString(Map.of("candidate", candidate, "sharedContext", work.sharedContext()));
        AiCall call = callWithTransientRetry(context, work, slot, phase, TaskType.CONCEPT_LEGAL_REVIEW, input, attemptId);
        if (call.result() == null && isSchema(call.failure())) {
            execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), ConceptAttemptError.SCHEMA_INVALID, false);
            String repairAttempt = execution.beginRetryAttempt(slot.slotId(), phase, context.taskRunId());
            call = callWithTransientRetry(context, work, slot, phase, TaskType.CONCEPT_LEGAL_REVIEW, input, repairAttempt);
        }
        if (call.result() == null) {
            ConceptAttemptError classification = classify(call.failure());
            if (classification == ConceptAttemptError.PERMANENT_PROVIDER_FAILURE) {
                execution.failSlot(work.runId(), slot.slotId(), call.attemptId(), classification, false, true);
                return new Review(ReviewOutcome.FAILED, null, null, call.attemptId());
            }
            execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), classification,
                classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE);
            return new Review(ReviewOutcome.REPLACE, null, null, call.attemptId());
        }
        LegalDisposition disposition = execution.legal(work.runId(), slot.slotId(), call.attemptId(), candidate, call.result());
        return new Review(ReviewOutcome.DISPOSITION, disposition, call.result(), call.attemptId());
    }

    private AiCall callWithTransientRetry(TaskRunWorkerContext context, Work work, SlotWork slot,
            ConceptAttemptPhase phase, TaskType type, String input, String attemptId) {
        try {
            return new AiCall(attemptId, ai.execute(type, input, context.correlationId(), attemptId), null);
        } catch (ExecutionFailure failure) {
            if (!failure.retryable()) return new AiCall(attemptId, null, failure);
            execution.recordAttemptError(work.runId(), slot.slotId(), attemptId, ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, true);
            String retryAttempt = execution.beginRetryAttempt(slot.slotId(), phase, context.taskRunId());
            try {
                return new AiCall(retryAttempt, ai.execute(type, input, context.correlationId(), retryAttempt), null);
            } catch (ExecutionFailure exhausted) {
                return new AiCall(retryAttempt, null, exhausted);
            }
        }
    }

    private boolean replace(TaskRunWorkerContext context, Work work, SlotWork slot, int currentRound, String attemptId) {
        if (currentRound >= ConceptFactoryLimits.MAX_REPLACEMENT_ROUNDS) {
            execution.recordAttemptError(work.runId(), slot.slotId(), attemptId,
                ConceptAttemptError.INTERNAL_EXECUTION_ERROR, false);
            execution.failSlot(work.runId(), slot.slotId(), null,
                ConceptAttemptError.INTERNAL_EXECUTION_ERROR, false, false);
            return false;
        }
        execution.beginReplacement(work.runId(), slot.slotId(), currentRound + 1);
        publishSlot(context, slot, "job.concept.slot.replacing");
        return true;
    }

    private void terminalize(TaskRunService.Claim claim, TaskRunWorkerContext context, Work work, WorkerOutcome outcome) {
        if (outcome == WorkerOutcome.NEEDS_INPUT) {
            execution.needsInput(work.runId());
            taskRuns.needsInput(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            publish(context, "NEEDS_INPUT", "job.concept.run.needs_input", JobEvent.Status.NEEDS_INPUT, Map.of(), null);
            return;
        }
        if (outcome == WorkerOutcome.FAILED || !execution.completeIfEligible(work.runId())) {
            terminalizeFailed(claim, context, work.runId(), "CONCEPT_SLOT_FAILED");
            return;
        }
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            "{\"status\":\"COMPLETED\"}", context.inputHash(), "1.0");
        publish(context, "COMPLETED", "job.concept.run.completed", JobEvent.Status.COMPLETED,
            Map.of("eligibleCount", ConceptFactoryLimits.SLOT_COUNT), null);
    }

    private void terminalizeFailed(TaskRunService.Claim claim, TaskRunWorkerContext context, String runId, String code) {
        try {
            execution.failRun(runId);
        } catch (RuntimeException domainFailure) {
            log.warn("Concept Factory run failure transition failed runId={} type={}", runId, domainFailure.getClass().getSimpleName());
        }
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "EXECUTION_FAILED", code, false);
        publish(context, "FAILED", "job.concept.run.failed", JobEvent.Status.FAILED, Map.of(), code);
    }

    private ConceptAttemptError classify(ExecutionFailure failure) {
        if (isSchema(failure)) return ConceptAttemptError.SCHEMA_INVALID;
        return failure.retryable() ? ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE
            : ConceptAttemptError.PERMANENT_PROVIDER_FAILURE;
    }

    private boolean isSchema(ExecutionFailure failure) {
        return "RESULT_SCHEMA_INVALID".equals(failure.code());
    }

    private void publishSlot(TaskRunWorkerContext context, SlotWork slot, String key) {
        publish(context, "SLOT_" + slot.slotNumber(), key, JobEvent.Status.RUNNING,
            Map.of("slot", slot.slotNumber()), null);
    }

    private void publish(TaskRunWorkerContext context, String stage, String key, JobEvent.Status status,
            Map<String, ?> params, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(), context.taskRunId(),
            stage, key, status, key, params, code));
    }

    enum WorkerOutcome { COMPLETED, NEEDS_INPUT, FAILED }
    enum SlotOutcome { ELIGIBLE, NEEDS_INPUT, FAILED }
    private enum GenerationOutcome { SUCCESS, REPLACE, FAILED }
    private enum ReviewOutcome { DISPOSITION, REPLACE, FAILED }
    private record AiCall(String attemptId, JsonNode result, ExecutionFailure failure) {}
    private record Generation(GenerationOutcome outcome, String attemptId, JsonNode result) {}
    private record Review(ReviewOutcome outcome, LegalDisposition disposition, JsonNode legal, String attemptId) {}
}
