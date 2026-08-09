package com.aivle.backend.pipeline.concept.worker;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.CandidateDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.LegalDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.SlotWork;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.Work;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase;
import com.aivle.backend.pipeline.concept.domain.ConceptFingerprint;
import com.aivle.backend.pipeline.concept.domain.ConceptSemanticDistinctnessResult;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryLimits;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.util.LinkedHashMap;
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
    private static final Duration PARENT_LEASE = Duration.ofMinutes(10);
    private static final Duration PARENT_TIMEOUT = Duration.ofMinutes(30);
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
            PARENT_LEASE, PARENT_TIMEOUT);
        if (claim == null) return false;
        TaskRunWorkerContext context = taskRuns.workerContext(claim.taskRunId());
        try {
            taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
            publish(context, "RUNNING", "job.concept.run.started", JobEvent.Status.RUNNING, Map.of(), null);
            publish(context, "LEGAL_CONTEXT", "job.concept.legal-context.started", JobEvent.Status.RUNNING, Map.of(), null);
            Work work = execution.prepare(context.subjectId(), context.projectId());
            publish(context, "LEGAL_CONTEXT", "job.concept.legal-context.completed", JobEvent.Status.RUNNING, Map.of(), null);
            terminalize(claim, context, work, processSlots(context, work, claim));
        } catch (RuntimeException failure) {
            log.warn("Concept Factory worker boundary taskRunId={} exceptionType={} exceptionMessage={}",
                claim.taskRunId(), failure.getClass().getSimpleName(), safeExceptionMessage(failure), failure);
            terminalizeFailed(claim, context, context.subjectId(), "INTERNAL_EXECUTION_ERROR");
        }
        return true;
    }

    WorkerOutcome processSlots(TaskRunWorkerContext context, Work work) {
        return processSlots(context, work, null);
    }

    private WorkerOutcome processSlots(TaskRunWorkerContext context, Work work, TaskRunService.Claim claim) {
        boolean failed = false;
        for (SlotWork slot : work.slots()) {
            heartbeat(claim);
            try {
                SlotOutcome outcome = processSlot(context, work, slot);
                if (outcome == SlotOutcome.NEEDS_INPUT) return failed ? WorkerOutcome.FAILED : WorkerOutcome.NEEDS_INPUT;
                if (outcome == SlotOutcome.FAILED) failed = true;
            } catch (RuntimeException isolated) {
                execution.failActiveAttempt(slot.slotId(), ConceptAttemptError.INTERNAL_STATE_FAILURE,
                    "INTERNAL_STATE_FAILURE");
                try {
                    var diagnostic = execution.diagnostic(work.runId(), slot.slotId());
                    log.warn("Concept slot isolated failure runId={} slotNumber={} runStatus={} slotStatus={} phase={} safeErrorCode={} exceptionType={} exceptionMessage={}",
                        work.runId(), slot.slotNumber(), diagnostic.runStatus(), diagnostic.slotStatus(),
                        diagnostic.phase(), diagnostic.safeErrorCode(), isolated.getClass().getSimpleName(),
                        safeExceptionMessage(isolated), isolated);
                } catch (RuntimeException diagnosticFailure) {
                    log.warn("Concept slot isolated failure runId={} slotNumber={} runStatus=UNKNOWN slotStatus=UNKNOWN phase=UNKNOWN safeErrorCode=INTERNAL_STATE_FAILURE exceptionType={} exceptionMessage={}",
                        work.runId(), slot.slotNumber(), isolated.getClass().getSimpleName(),
                        safeExceptionMessage(isolated), isolated);
                }
                execution.failSlot(work.runId(), slot.slotId(), null, ConceptAttemptError.INTERNAL_EXECUTION_ERROR, false, false);
                failed = true;
            }
        }
        heartbeat(claim);
        return failed ? WorkerOutcome.FAILED : WorkerOutcome.COMPLETED;
    }

    private void heartbeat(TaskRunService.Claim claim) {
        if (claim != null) taskRuns.heartbeat(claim.taskRunId(), claim.taskAttemptId(),
            claim.claimToken(), PARENT_LEASE);
    }

    SlotOutcome processSlot(TaskRunWorkerContext context, Work work, SlotWork slot) {
        for (int replacementRound = 0; replacementRound <= ConceptFactoryLimits.MAX_REPLACEMENT_ROUNDS; replacementRound++) {
            ConceptAttemptPhase phase = replacementRound == 0 ? ConceptAttemptPhase.INITIAL : ConceptAttemptPhase.REPLACEMENT;
            JsonNode candidate;
            String attemptId;
            if (replacementRound == 0 && slot.candidateJson() != null) {
                candidate = mapper.readTree(slot.candidateJson());
                attemptId = slot.candidateAttemptId();
            } else {
                execution.recordCandidateInspection(work.runId());
                Generation generation = generate(context, work, slot, phase, TaskType.CONCEPT_CANDIDATE,
                    mapper.writeValueAsString(Map.of(
                        "ideaBriefSnapshotId", work.snapshotId(),
                        "generationStrategy", work.generationStrategy().name(),
                        "candidateIndex", slot.slotNumber(),
                        "originalCandidate", work.generationStrategy().name().equals("AS_IS") && slot.slotNumber() == 1,
                        "diversityFocus", slot.focus().name(),
                        "fields", work.fields(),
                        "acceptedConceptFingerprints", execution.acceptedFingerprints(work.runId()),
                        "rejectedConceptFingerprints", execution.rejectedFingerprints(work.runId()),
                        "currentSlotPreviousFingerprints", execution.currentSlotPreviousFingerprints(slot.slotId()))));
                if (generation.outcome() == GenerationOutcome.FAILED) return SlotOutcome.FAILED;
                if (generation.outcome() == GenerationOutcome.REPLACE) {
                    if (!replace(context, work, slot, replacementRound, generation.attemptId(),
                        generation.failureClassification())) return SlotOutcome.FAILED;
                    continue;
                }
                candidate = generation.result();
                attemptId = generation.attemptId();
                execution.generated(slot.slotId(), attemptId, candidate);
                publishAttempt(context, slot, "job.concept.slot.generated", TaskType.CONCEPT_CANDIDATE,
                    attemptId, "SUCCESS", generation.durationMs(), null);
            }

            publishSlot(context, slot, "job.concept.slot.validating_origin");
            CandidateDisposition candidateDisposition = validateDistinctness(
                context, work, slot, attemptId, candidate);
            if (candidateDisposition == null) return SlotOutcome.FAILED;
            if (candidateDisposition != CandidateDisposition.ACCEPTED) {
                publishSlot(context, slot, "job.concept.slot.rejected");
                ConceptAttemptError exhaustion = candidateDisposition == CandidateDisposition.DUPLICATE
                    ? ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS
                    : candidateDisposition == CandidateDisposition.LOCKED_INVALID
                        ? ConceptAttemptError.LOCKED_CONSTRAINT_INVALID : ConceptAttemptError.ORIGIN_INVALID;
                if (!replace(context, work, slot, replacementRound, attemptId, exhaustion)) return SlotOutcome.FAILED;
                continue;
            }
            publishSlot(context, slot, "job.concept.slot.validating_distinctness");
            publishSlot(context, slot, "job.concept.slot.validating_legal");

            Review review = review(context, work, slot, phase, attemptId, candidate);
            if (review.outcome() == ReviewOutcome.FAILED) return SlotOutcome.FAILED;
            if (review.outcome() == ReviewOutcome.REPLACE) {
                publishAttempt(context, slot, "job.concept.slot.rejected", TaskType.CONCEPT_LEGAL_REVIEW,
                    review.attemptId(), "REJECTED", review.durationMs(), null);
                if (!replace(context, work, slot, replacementRound, review.attemptId(),
                    review.failureClassification())) return SlotOutcome.FAILED;
                continue;
            }
            if (review.disposition() == LegalDisposition.NEEDS_INPUT) return SlotOutcome.NEEDS_INPUT;
            if (review.disposition() == LegalDisposition.ELIGIBLE) {
                publishAttempt(context, slot, "job.concept.slot.eligible", TaskType.CONCEPT_LEGAL_REVIEW,
                    review.attemptId(), "SUCCESS", review.durationMs(), null);
                return SlotOutcome.ELIGIBLE;
            }

            if (review.disposition() == LegalDisposition.REDESIGN && slot.redesignCount() == 0) {
                publishAttempt(context, slot, "job.concept.slot.redesigning", TaskType.CONCEPT_LEGAL_REVIEW,
                    review.attemptId(), "REDESIGN_REQUIRED", review.durationMs(), null);
                var reviewedPattern = execution.legalFactPattern(candidate);
                Generation redesign = generate(context, work, slot, ConceptAttemptPhase.REDESIGN, TaskType.CONCEPT_REDESIGN,
                    mapper.writeValueAsString(Map.of("candidate", candidate,
                        "safeConstraints", review.legal().path("requiredControls"),
                        "prohibitedVariants", review.legal().path("prohibitedVariants"),
                        "designGaps", review.legal().path("redesignRequirements"),
                        "legalFactPattern", reviewedPattern.factPattern())));
                if (redesign.outcome() == GenerationOutcome.FAILED) return SlotOutcome.FAILED;
                attemptId = redesign.attemptId();
                if (redesign.outcome() == GenerationOutcome.SUCCESS) {
                    execution.generated(slot.slotId(), redesign.attemptId(), redesign.result());
                    publishAttempt(context, slot, "job.concept.slot.generated", TaskType.CONCEPT_REDESIGN,
                        redesign.attemptId(), "SUCCESS", redesign.durationMs(), null);
                    publishSlot(context, slot, "job.concept.slot.validating_origin");
                    CandidateDisposition redesignedValidation = validateDistinctness(
                        context, work, slot, redesign.attemptId(), redesign.result());
                    if (redesignedValidation == null) return SlotOutcome.FAILED;
                    if (redesignedValidation != CandidateDisposition.ACCEPTED) {
                        publishSlot(context, slot, "job.concept.slot.rejected");
                        ConceptAttemptError exhaustion = redesignedValidation == CandidateDisposition.DUPLICATE
                            ? ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS
                            : redesignedValidation == CandidateDisposition.LOCKED_INVALID
                                ? ConceptAttemptError.LOCKED_CONSTRAINT_INVALID : ConceptAttemptError.ORIGIN_INVALID;
                        if (!replace(context, work, slot, replacementRound, redesign.attemptId(), exhaustion)) {
                            return SlotOutcome.FAILED;
                        }
                        continue;
                    }
                    publishSlot(context, slot, "job.concept.slot.validating_distinctness");
                    publishSlot(context, slot, "job.concept.slot.validating_legal");
                    Review redesigned = review(context, work, slot, ConceptAttemptPhase.REDESIGN,
                        redesign.attemptId(), redesign.result());
                    if (redesigned.outcome() == ReviewOutcome.FAILED) return SlotOutcome.FAILED;
                    if (redesigned.outcome() == ReviewOutcome.REPLACE) {
                        publishAttempt(context, slot, "job.concept.slot.rejected", TaskType.CONCEPT_LEGAL_REVIEW,
                            redesigned.attemptId(), "REJECTED", redesigned.durationMs(), null);
                        if (!replace(context, work, slot, replacementRound, redesigned.attemptId(),
                                redesigned.failureClassification())) return SlotOutcome.FAILED;
                        continue;
                    }
                    if (redesigned.disposition() == LegalDisposition.NEEDS_INPUT) return SlotOutcome.NEEDS_INPUT;
                    if (redesigned.disposition() == LegalDisposition.ELIGIBLE) {
                        publishAttempt(context, slot, "job.concept.slot.eligible", TaskType.CONCEPT_LEGAL_REVIEW,
                            redesigned.attemptId(), "SUCCESS", redesigned.durationMs(), null);
                        return SlotOutcome.ELIGIBLE;
                    }
                }
            }

            publishSlot(context, slot, "job.concept.slot.rejected");
            if (!replace(context, work, slot, replacementRound, attemptId,
                ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED)) return SlotOutcome.FAILED;
        }
        throw new IllegalStateException("bounded replacement loop did not terminate");
    }

    /** Applies the same schema/origin/deterministic/semantic gate to initial, replacement and redesign candidates. */
    private CandidateDisposition validateDistinctness(TaskRunWorkerContext context, Work work, SlotWork slot,
            String attemptId, JsonNode candidate) {
        CandidateDisposition disposition = execution.validateCandidate(
            work.runId(), slot.slotId(), attemptId, candidate, work.generationStrategy(),
            slot.slotNumber(), work.fields());
        if (disposition != CandidateDisposition.SEMANTIC_REVIEW_REQUIRED) return disposition;

        boolean duplicate = false;
        for (Map<String, Object> existing : execution.semanticComparisons(
                work.runId(), slot.slotId(), attemptId, candidate)) {
            try {
                JsonNode judged = ai.execute(TaskType.CONCEPT_DISTINCTNESS_JUDGE,
                    mapper.writeValueAsString(Map.of(
                        "candidateA", existing,
                        "candidateB", ConceptFingerprint.businessSummary(candidate))),
                    context.correlationId(), attemptId + "-distinctness");
                duplicate = ConceptSemanticDistinctnessResult.validate(judged)
                    == ConceptSemanticDistinctnessResult.Decision.DUPLICATE;
            } catch (RuntimeException failure) {
                execution.recordAttemptError(work.runId(), slot.slotId(), attemptId,
                    ConceptAttemptError.RESULT_SCHEMA_INVALID, "SEMANTIC_DISTINCTNESS_REVIEW_FAILED", false);
                return null;
            }
            if (duplicate) break;
        }
        if (duplicate) {
            execution.rejectSemanticDuplicate(slot.slotId(), attemptId, candidate);
            return CandidateDisposition.DUPLICATE;
        }
        execution.acceptSemanticDistinctness(slot.slotId());
        return CandidateDisposition.ACCEPTED;
    }

    private Generation generate(TaskRunWorkerContext context, Work work, SlotWork slot, ConceptAttemptPhase phase,
            TaskType type, String input) {
        String attemptId = execution.beginAttempt(slot.slotId(), phase, context.taskRunId());
        publishAttempt(context, slot, "job.concept.slot.started", type, attemptId,
            "START", null, null);
        AiCall call = callWithTransientRetry(context, work, slot, phase, type, input, attemptId);
        if (call.result() != null) return new Generation(GenerationOutcome.SUCCESS, call.attemptId(),
            call.result(), null, call.durationMs());
        return generationFailure(work, slot, call);
    }

    private Generation generationFailure(Work work, SlotWork slot, AiCall call) {
        ConceptAttemptError classification = classify(call.failure());
        publishAttempt(call.context(), slot, "job.concept.slot.generation_failed", call.taskType(),
            call.attemptId(), "FAILED", call.durationMs(), call.failure());
        if (classification == ConceptAttemptError.PERMANENT_PROVIDER_FAILURE) {
            execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), classification,
                call.failure().reason(), false);
            execution.failSlot(work.runId(), slot.slotId(), null, classification, false, true);
            return new Generation(GenerationOutcome.FAILED, call.attemptId(), null, classification, call.durationMs());
        }
        execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), classification,
            call.failure().reason(), classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE);
        return new Generation(GenerationOutcome.REPLACE, call.attemptId(), null, classification, call.durationMs());
    }

    private Review review(TaskRunWorkerContext context, Work work, SlotWork slot, ConceptAttemptPhase phase,
            String attemptId, JsonNode candidate) {
        var factPattern = execution.legalFactPattern(candidate);
        String input = mapper.writeValueAsString(Map.of(
            "legalFactPattern", factPattern.factPattern(),
            "factPatternHash", factPattern.factPatternHash(),
            "externalFactContext", work.externalFactContext()
        ));
        String reviewAttemptId = execution.beginLegalReviewAttempt(slot.slotId(), context.taskRunId());
        AiCall call = callWithTransientRetry(context, work, slot, ConceptAttemptPhase.LEGAL_REVIEW,
            TaskType.CONCEPT_LEGAL_REVIEW, input, reviewAttemptId);
        if (call.result() == null) {
            ConceptAttemptError classification = classifyLegal(call.failure());
            execution.failLegalReview(work.runId(), slot.slotId(), call.attemptId(), classification,
                call.failure().reason(), call.failure().retryable() || isSchema(call.failure()));
            publishAttempt(context, slot, "job.concept.slot.review_failed", TaskType.CONCEPT_LEGAL_REVIEW,
                call.attemptId(), "FAILED", call.durationMs(), call.failure());
            return new Review(ReviewOutcome.FAILED, null, null, call.attemptId(), classification,
                call.durationMs());
        }
        LegalDisposition disposition = execution.legal(work.runId(), slot.slotId(), call.attemptId(), candidate, call.result());
        ConceptAttemptError replacementError = null;
        if (disposition == LegalDisposition.REPLACE) {
            replacementError = "NEEDS_FACTS".equals(call.result().path("status").asText())
                ? ConceptAttemptError.LEGAL_EXTERNAL_FACT_UNRESOLVED : ConceptAttemptError.LEGAL_REJECTED;
        }
        return new Review(disposition == LegalDisposition.REPLACE ? ReviewOutcome.REPLACE : ReviewOutcome.DISPOSITION,
            disposition, call.result(), call.attemptId(), replacementError, call.durationMs());
    }

    private AiCall callWithTransientRetry(TaskRunWorkerContext context, Work work, SlotWork slot,
            ConceptAttemptPhase phase, TaskType type, String input, String attemptId) {
        long started = System.nanoTime();
        try {
            return new AiCall(attemptId, ai.execute(type, input, context.correlationId(), attemptId), null,
                elapsedMillis(started), context, type);
        } catch (ExecutionFailure failure) {
            if (!failure.retryable()) return new AiCall(attemptId, null, failure,
                elapsedMillis(started), context, type);
            execution.recordAttemptError(work.runId(), slot.slotId(), attemptId,
                ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, failure.reason(), true);
            String retryAttempt = execution.beginRetryAttempt(slot.slotId(), phase, context.taskRunId());
            publishAttempt(context, slot, "job.concept.slot.retrying", type, retryAttempt,
                "RETRY", null, failure);
            long retryStarted = System.nanoTime();
            try {
                return new AiCall(retryAttempt, ai.execute(type, input, context.correlationId(), retryAttempt), null,
                    elapsedMillis(retryStarted), context, type);
            } catch (ExecutionFailure exhausted) {
                return new AiCall(retryAttempt, null, exhausted, elapsedMillis(retryStarted), context, type);
            }
        }
    }

    private boolean replace(TaskRunWorkerContext context, Work work, SlotWork slot, int currentRound,
            String attemptId, ConceptAttemptError exhaustionError) {
        if (currentRound >= ConceptFactoryLimits.MAX_REPLACEMENT_ROUNDS) {
            // The current attempt already owns the root cause. Slot exhaustion must not overwrite it.
            execution.failSlot(work.runId(), slot.slotId(), null,
                exhaustionError, false, false);
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
            terminalizeFailed(claim, context, work.runId(), execution.failureCode(work.runId()));
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
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            taskFailureFamily(code), code, false);
        publish(context, "FAILED", "job.concept.run.failed", JobEvent.Status.FAILED, Map.of(), code);
    }

    private ConceptAttemptError classify(ExecutionFailure failure) {
        if (isSchema(failure)) return ConceptAttemptError.SCHEMA_INVALID;
        return failure.retryable() ? ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE
            : ConceptAttemptError.PERMANENT_PROVIDER_FAILURE;
    }

    private ConceptAttemptError classifyLegal(ExecutionFailure failure) {
        if (isSchema(failure)) return ConceptAttemptError.RESULT_SCHEMA_INVALID;
        if (failure.reason().startsWith("MOLEG_") || failure.reason().startsWith("LEGAL_SOURCE_")) {
            return ConceptAttemptError.LEGAL_SOURCE_FAILURE;
        }
        return failure.retryable() ? ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE
            : ConceptAttemptError.PERMANENT_PROVIDER_FAILURE;
    }

    private boolean isSchema(ExecutionFailure failure) {
        return "RESULT_SCHEMA_INVALID".equals(failure.code());
    }

    private void publishSlot(TaskRunWorkerContext context, SlotWork slot, String key) {
        publish(context, "SLOT_" + slot.slotNumber(), key, JobEvent.Status.RUNNING,
            Map.of("slot", slot.slotNumber(), "slotNumber", slot.slotNumber(),
                "slotId", slot.slotId(), "variationFocus", slot.focus().name(),
                "runId", context.subjectId(), "correlationId", context.correlationId(),
                "event", eventName(key)), null);
    }

    private void publishAttempt(TaskRunWorkerContext context, SlotWork slot, String key, TaskType taskType,
            String attemptId, String event, Long durationMs, ExecutionFailure failure) {
        var trace = execution.attemptTrace(attemptId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("slot", slot.slotNumber());
        params.put("slotNumber", slot.slotNumber());
        params.put("slotId", slot.slotId());
        params.put("variationFocus", slot.focus().name());
        params.put("runId", context.subjectId());
        params.put("phase", trace == null ? "UNKNOWN" : trace.phase());
        params.put("taskType", taskType.name());
        params.put("attemptNumber", trace == null ? 0 : trace.attemptNumber());
        params.put("conceptAttemptId", attemptId);
        params.put("aiTaskAttemptId", attemptId);
        params.put("correlationId", context.correlationId());
        params.put("event", event);
        if (durationMs != null) params.put("durationMs", durationMs);
        if (failure != null) {
            params.put("safeErrorCode", failure.code());
            params.put("safeReason", failure.reason());
            params.put("retryable", failure.retryable());
            if (!failure.validationFields().isEmpty()) {
                params.put("failedField", failure.validationFields().get(0).path());
            }
        } else if (trace != null && trace.safeErrorCode() != null) {
            params.put("safeErrorCode", trace.safeErrorCode());
            params.put("retryable", trace.retryable());
        }
        publish(context, "SLOT_" + slot.slotNumber(), key, JobEvent.Status.RUNNING,
            params, failure == null ? null : failure.code());
    }

    private void publish(TaskRunWorkerContext context, String stage, String key, JobEvent.Status status,
            Map<String, ?> params, String code) {
        events.publish(new JobEventPublisher.Command(context.projectId(), context.taskRunId(), context.taskRunId(),
            stage, key, status, key, params, code));
    }

    private String eventName(String key) {
        int separator = key.lastIndexOf('.');
        return separator < 0 ? key.toUpperCase() : key.substring(separator + 1).toUpperCase();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private String safeExceptionMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return "NO_MESSAGE";
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 300));
    }

    private String taskFailureFamily(String code) {
        if (code == null) return "EXECUTION_FAILED";
        return switch (code) {
            case "SCHEMA_INVALID", "RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED",
                 "PROVIDER_JSON_INVALID", "PYDANTIC_RESULT_VALIDATION_FAILED", "CONTENT_FIELD_MISSING",
                 "VALUE_SEMANTICS_INCOMPLETE", "GOVERNANCE_SEMANTICS_MISMATCH",
                 "CANDIDATE_METADATA_INVALID" -> "RESULT_SCHEMA_INVALID";
            case "MODEL_DEPENDENCY_UNAVAILABLE", "TRANSIENT_PROVIDER_FAILURE" -> "DEPENDENCY_UNAVAILABLE";
            default -> "EXECUTION_FAILED";
        };
    }

    enum WorkerOutcome { COMPLETED, NEEDS_INPUT, FAILED }
    enum SlotOutcome { ELIGIBLE, NEEDS_INPUT, FAILED }
    private enum GenerationOutcome { SUCCESS, REPLACE, FAILED }
    private enum ReviewOutcome { DISPOSITION, REPLACE, FAILED }
    private record AiCall(String attemptId, JsonNode result, ExecutionFailure failure, long durationMs,
                          TaskRunWorkerContext context, TaskType taskType) {}
    private record Generation(GenerationOutcome outcome, String attemptId, JsonNode result,
                              ConceptAttemptError failureClassification, long durationMs) {}
    private record Review(ReviewOutcome outcome, LegalDisposition disposition, JsonNode legal, String attemptId,
                          ConceptAttemptError failureClassification, long durationMs) {}
}
