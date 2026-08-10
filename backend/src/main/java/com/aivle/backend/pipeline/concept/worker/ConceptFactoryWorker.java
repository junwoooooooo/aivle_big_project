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
import java.util.Set;
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
    private final ProviderRetryPolicy providerRetryPolicy;
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
                if (outcome == SlotOutcome.RETRY_LATER) return WorkerOutcome.RETRY_LATER;
                if (outcome == SlotOutcome.FATAL_FAILURE) return WorkerOutcome.FATAL_FAILURE;
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
        Map<String, Object> replacementContext = null;
        for (int replacementRound = slot.replacementRounds(); replacementRound <= ConceptFactoryLimits.MAX_REPLACEMENT_ROUNDS; replacementRound++) {
            ConceptAttemptPhase phase = replacementRound == 0 ? ConceptAttemptPhase.INITIAL : ConceptAttemptPhase.REPLACEMENT;
            JsonNode candidate;
            String attemptId;
            if (slot.candidateJson() != null) {
                candidate = mapper.readTree(slot.candidateJson());
                attemptId = slot.candidateAttemptId();
            } else {
                Map<String, Object> generationInput = new LinkedHashMap<>();
                generationInput.put("ideaBriefSnapshotId", work.snapshotId());
                generationInput.put("generationStrategy", work.generationStrategy().name());
                generationInput.put("candidateIndex", slot.slotNumber());
                generationInput.put("originalCandidate", work.generationStrategy().name().equals("AS_IS") && slot.slotNumber() == 1);
                generationInput.put("diversityFocus", slot.focus().name());
                generationInput.put("fields", work.fields());
                generationInput.put("acceptedConceptFingerprints", safeList(execution.eligibleConceptFingerprints(work.runId())));
                generationInput.put("rejectedConceptFingerprints", safeList(execution.softRejectedExamples(work.runId(), slot.slotId())));
                generationInput.put("currentSlotPreviousFingerprints", safeList(execution.currentSlotSearchHistory(slot.slotId())));
                if (replacementContext != null) generationInput.put("replacementContext", replacementContext);
                Generation generation = generate(context, work, slot, phase, TaskType.CONCEPT_CANDIDATE,
                    mapper.writeValueAsString(generationInput));
                if (generation.outcome() == GenerationOutcome.TRANSIENT_PROVIDER_FAILURE) return SlotOutcome.RETRY_LATER;
                if (generation.outcome() == GenerationOutcome.REQUEST_CONTRACT_FAILURE) return SlotOutcome.FATAL_FAILURE;
                if (generation.outcome() == GenerationOutcome.RUN_FATAL_PROVIDER_FAILURE) return SlotOutcome.FATAL_FAILURE;
                if (generation.outcome() == GenerationOutcome.PERMANENT_PROVIDER_FAILURE) return SlotOutcome.FAILED;
                if (generation.outcome() == GenerationOutcome.DOMAIN_FAILURE) {
                    if (!replace(context, work, slot, replacementRound, generation.attemptId(),
                        generation.failureClassification())) return SlotOutcome.FAILED;
                    continue;
                }
                candidate = generation.result();
                attemptId = generation.attemptId();
                execution.generated(slot.slotId(), attemptId, candidate);
                execution.recordCandidateInspection(work.runId());
                publishAttempt(context, slot, "job.concept.slot.generated", TaskType.CONCEPT_CANDIDATE,
                    attemptId, "SUCCESS", generation.durationMs(), null);
            }

            publishSlot(context, slot, "job.concept.slot.validating_origin");
            CandidateDisposition candidateDisposition = validateDistinctness(
                context, work, slot, attemptId, candidate);
            if (candidateDisposition == null) return SlotOutcome.FAILED;
            if (candidateDisposition == CandidateDisposition.PROVIDER_RETRY_LATER) return SlotOutcome.RETRY_LATER;
            if (candidateDisposition == CandidateDisposition.REQUEST_CONTRACT_FAILURE) return SlotOutcome.FATAL_FAILURE;
            if (candidateDisposition == CandidateDisposition.PROVIDER_FATAL_FAILURE) return SlotOutcome.FATAL_FAILURE;
            if (candidateDisposition == CandidateDisposition.PROVIDER_PERMANENT_FAILURE) return SlotOutcome.FAILED;
            if (candidateDisposition != CandidateDisposition.ACCEPTED) {
                ConceptAttemptError exhaustion = candidateDisposition == CandidateDisposition.DUPLICATE
                    ? ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS
                    : candidateDisposition == CandidateDisposition.LOCKED_INVALID
                        ? ConceptAttemptError.LOCKED_CONSTRAINT_INVALID : ConceptAttemptError.ORIGIN_INVALID;
                publishRejection(context, slot, attemptId, exhaustion);
                replacementContext = execution.replacementFeedback(work.runId(), slot.slotId(), attemptId,
                    candidate, exhaustion, replacementRound + 1);
                if (!replace(context, work, slot, replacementRound, attemptId, exhaustion)) return SlotOutcome.FAILED;
                continue;
            }
            execution.recordCompletedRedesign(slot.slotId(), attemptId);
            publishSlot(context, slot, "job.concept.slot.validating_distinctness");
            publishSlot(context, slot, "job.concept.slot.validating_legal");

            Review review = review(context, work, slot, phase, attemptId, candidate);
            if (review.outcome() == ReviewOutcome.TRANSIENT_PROVIDER_FAILURE) return SlotOutcome.RETRY_LATER;
            if (review.outcome() == ReviewOutcome.REQUEST_CONTRACT_FAILURE) return SlotOutcome.FATAL_FAILURE;
            if (review.outcome() == ReviewOutcome.RUN_FATAL_PROVIDER_FAILURE) return SlotOutcome.FATAL_FAILURE;
            if (review.outcome() == ReviewOutcome.PERMANENT_PROVIDER_FAILURE) return SlotOutcome.FAILED;
            if (review.outcome() == ReviewOutcome.REPLACE) {
                publishAttempt(context, slot, "job.concept.slot.rejected", TaskType.CONCEPT_LEGAL_REVIEW,
                    review.attemptId(), "REJECTED", review.durationMs(), null);
                replacementContext = execution.replacementFeedback(work.runId(), slot.slotId(), review.attemptId(),
                    candidate, review.failureClassification(), replacementRound + 1);
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
                if (redesign.outcome() == GenerationOutcome.TRANSIENT_PROVIDER_FAILURE) return SlotOutcome.RETRY_LATER;
                if (redesign.outcome() == GenerationOutcome.REQUEST_CONTRACT_FAILURE) return SlotOutcome.FATAL_FAILURE;
                if (redesign.outcome() == GenerationOutcome.RUN_FATAL_PROVIDER_FAILURE) return SlotOutcome.FATAL_FAILURE;
                if (redesign.outcome() == GenerationOutcome.PERMANENT_PROVIDER_FAILURE) return SlotOutcome.FAILED;
                if (redesign.outcome() == GenerationOutcome.DOMAIN_FAILURE) {
                    if (!replace(context, work, slot, replacementRound, redesign.attemptId(),
                            redesign.failureClassification())) return SlotOutcome.FAILED;
                    continue;
                }
                attemptId = redesign.attemptId();
                if (redesign.outcome() == GenerationOutcome.SUCCESS) {
                    execution.generated(slot.slotId(), redesign.attemptId(), redesign.result());
                    execution.recordCandidateInspection(work.runId());
                    publishAttempt(context, slot, "job.concept.slot.generated", TaskType.CONCEPT_REDESIGN,
                        redesign.attemptId(), "SUCCESS", redesign.durationMs(), null);
                    publishSlot(context, slot, "job.concept.slot.validating_origin");
                    CandidateDisposition redesignedValidation = validateDistinctness(
                        context, work, slot, redesign.attemptId(), redesign.result());
                    if (redesignedValidation == null) return SlotOutcome.FAILED;
                    if (redesignedValidation == CandidateDisposition.PROVIDER_RETRY_LATER) return SlotOutcome.RETRY_LATER;
                    if (redesignedValidation == CandidateDisposition.REQUEST_CONTRACT_FAILURE) return SlotOutcome.FATAL_FAILURE;
                    if (redesignedValidation == CandidateDisposition.PROVIDER_FATAL_FAILURE) return SlotOutcome.FATAL_FAILURE;
                    if (redesignedValidation == CandidateDisposition.PROVIDER_PERMANENT_FAILURE) return SlotOutcome.FAILED;
                    if (redesignedValidation != CandidateDisposition.ACCEPTED) {
                        ConceptAttemptError exhaustion = redesignedValidation == CandidateDisposition.DUPLICATE
                            ? ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS
                            : redesignedValidation == CandidateDisposition.LOCKED_INVALID
                                ? ConceptAttemptError.LOCKED_CONSTRAINT_INVALID : ConceptAttemptError.ORIGIN_INVALID;
                        publishRejection(context, slot, redesign.attemptId(), exhaustion);
                        replacementContext = execution.replacementFeedback(work.runId(), slot.slotId(),
                            redesign.attemptId(), redesign.result(), exhaustion, replacementRound + 1);
                        if (!replace(context, work, slot, replacementRound, redesign.attemptId(), exhaustion)) {
                            return SlotOutcome.FAILED;
                        }
                        continue;
                    }
                    execution.recordCompletedRedesign(slot.slotId(), redesign.attemptId());
                    publishSlot(context, slot, "job.concept.slot.validating_distinctness");
                    publishSlot(context, slot, "job.concept.slot.validating_legal");
                    Review redesigned = review(context, work, slot, ConceptAttemptPhase.REDESIGN,
                        redesign.attemptId(), redesign.result());
                    if (redesigned.outcome() == ReviewOutcome.TRANSIENT_PROVIDER_FAILURE) return SlotOutcome.RETRY_LATER;
                    if (redesigned.outcome() == ReviewOutcome.REQUEST_CONTRACT_FAILURE) return SlotOutcome.FATAL_FAILURE;
                    if (redesigned.outcome() == ReviewOutcome.RUN_FATAL_PROVIDER_FAILURE) return SlotOutcome.FATAL_FAILURE;
                    if (redesigned.outcome() == ReviewOutcome.PERMANENT_PROVIDER_FAILURE) return SlotOutcome.FAILED;
                    if (redesigned.outcome() == ReviewOutcome.REPLACE) {
                        publishAttempt(context, slot, "job.concept.slot.rejected", TaskType.CONCEPT_LEGAL_REVIEW,
                            redesigned.attemptId(), "REJECTED", redesigned.durationMs(), null);
                        replacementContext = execution.replacementFeedback(work.runId(), slot.slotId(),
                            redesigned.attemptId(), redesign.result(), redesigned.failureClassification(), replacementRound + 1);
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

            execution.discardCandidate(slot.slotId(), attemptId, ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED,
                "허용된 법률 재설계를 모두 사용했으나 적격 상태에 도달하지 못해 후보를 폐기합니다.");
            publishRejection(context, slot, attemptId, ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED);
            replacementContext = execution.replacementFeedback(work.runId(), slot.slotId(), attemptId,
                candidate, ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED, replacementRound + 1);
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
            String judgeAttemptId = execution.beginRetryAttempt(slot.slotId(), ConceptAttemptPhase.REPAIR,
                context.taskRunId());
            AiCall call = callWithTransientRetry(context, work, slot, ConceptAttemptPhase.REPAIR,
                TaskType.CONCEPT_DISTINCTNESS_JUDGE, mapper.writeValueAsString(Map.of(
                    "candidateA", existing,
                    "candidateB", ConceptFingerprint.businessSummary(candidate))), judgeAttemptId);
            if (call.result() == null) {
                ConceptAttemptError classification = classify(call.failure());
                execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), classification,
                    safeAttemptCode(classification, call.failure()),
                    classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE);
                publishAttempt(context, slot, "job.concept.slot.generation_failed", TaskType.CONCEPT_DISTINCTNESS_JUDGE,
                    call.attemptId(), "FAILED", call.durationMs(), call.failure());
                if (classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE) {
                    execution.pauseGenerationForRetry(slot.slotId());
                    return CandidateDisposition.PROVIDER_RETRY_LATER;
                }
                execution.failSlot(work.runId(), slot.slotId(), null, classification, false, true);
                return classification == ConceptAttemptError.REQUEST_CONTRACT_INVALID
                    ? CandidateDisposition.REQUEST_CONTRACT_FAILURE
                    : isRunGlobalProviderConfiguration(call.failure())
                        ? CandidateDisposition.PROVIDER_FATAL_FAILURE
                    : CandidateDisposition.PROVIDER_PERMANENT_FAILURE;
            }
            try {
                duplicate = ConceptSemanticDistinctnessResult.validate(call.result())
                    == ConceptSemanticDistinctnessResult.Decision.DUPLICATE;
            } catch (RuntimeException invalidResult) {
                execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(),
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
        boolean retryable = classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE;
        execution.recordAttemptError(work.runId(), slot.slotId(), call.attemptId(), classification,
            safeAttemptCode(classification, call.failure()), retryable);
        publishAttempt(call.context(), slot, "job.concept.slot.generation_failed", call.taskType(),
            call.attemptId(), "FAILED", call.durationMs(), call.failure());
        if (classification == ConceptAttemptError.REQUEST_CONTRACT_INVALID) {
            execution.failSlot(work.runId(), slot.slotId(), null, classification, false, true);
            return new Generation(GenerationOutcome.REQUEST_CONTRACT_FAILURE, call.attemptId(), null,
                classification, call.durationMs());
        }
        if (classification == ConceptAttemptError.PERMANENT_PROVIDER_FAILURE) {
            execution.failSlot(work.runId(), slot.slotId(), null, classification, false, true);
            return new Generation(isRunGlobalProviderConfiguration(call.failure())
                ? GenerationOutcome.RUN_FATAL_PROVIDER_FAILURE : GenerationOutcome.PERMANENT_PROVIDER_FAILURE,
                call.attemptId(), null, classification, call.durationMs());
        }
        if (classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE) {
            execution.pauseGenerationForRetry(slot.slotId());
        }
        return new Generation(classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE
            ? GenerationOutcome.TRANSIENT_PROVIDER_FAILURE : GenerationOutcome.DOMAIN_FAILURE,
            call.attemptId(), null, classification, call.durationMs());
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
                safeAttemptCode(classification, call.failure()),
                classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE);
            publishAttempt(context, slot, "job.concept.slot.review_failed", TaskType.CONCEPT_LEGAL_REVIEW,
                call.attemptId(), "FAILED", call.durationMs(), call.failure());
            if (classification == ConceptAttemptError.REQUEST_CONTRACT_INVALID) {
                execution.failSlot(work.runId(), slot.slotId(), null, classification, false, true);
                return new Review(ReviewOutcome.REQUEST_CONTRACT_FAILURE, null, null, call.attemptId(),
                    classification, call.durationMs());
            }
            return new Review(classification == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE
                    ? ReviewOutcome.TRANSIENT_PROVIDER_FAILURE
                    : isRunGlobalProviderConfiguration(call.failure())
                        ? ReviewOutcome.RUN_FATAL_PROVIDER_FAILURE : ReviewOutcome.PERMANENT_PROVIDER_FAILURE,
                null, null, call.attemptId(), classification, call.durationMs());
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
        String currentAttemptId = attemptId;
        int retries = 0;
        while (true) {
            try {
                return new AiCall(currentAttemptId,
                    ai.execute(type, input, context.correlationId(), currentAttemptId), null,
                    elapsedMillis(started), context, type);
            } catch (ExecutionFailure failure) {
                if (!failure.retryable() || !providerRetryPolicy.canRetry(retries)) {
                    return new AiCall(currentAttemptId, null, failure,
                        elapsedMillis(started), context, type);
                }
                execution.recordAttemptError(work.runId(), slot.slotId(), currentAttemptId,
                    ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE, failure.reason(), true);
                execution.recordProviderTransientRetry(work.runId());
                retries++;
                providerRetryPolicy.pause(retries, failure.retryAfterMillis());
                currentAttemptId = execution.beginRetryAttempt(slot.slotId(), phase, context.taskRunId());
                publishAttempt(context, slot, "job.concept.slot.retrying", type, currentAttemptId,
                    "RETRY", null, failure);
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
        if (outcome == WorkerOutcome.RETRY_LATER) {
            terminalizeFailed(claim, context, work.runId(), execution.failureCode(work.runId()), true);
            return;
        }
        if (outcome == WorkerOutcome.FAILED || outcome == WorkerOutcome.FATAL_FAILURE
                || !execution.completeIfEligible(work.runId())) {
            terminalizeFailed(claim, context, work.runId(), execution.failureCode(work.runId()), false);
            return;
        }
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            "{\"status\":\"COMPLETED\"}", context.inputHash(), "1.0");
        publish(context, "COMPLETED", "job.concept.run.completed", JobEvent.Status.COMPLETED,
            Map.of("eligibleCount", ConceptFactoryLimits.SLOT_COUNT), null);
    }

    private void terminalizeFailed(TaskRunService.Claim claim, TaskRunWorkerContext context, String runId, String code) {
        terminalizeFailed(claim, context, runId, code, false);
    }

    private void terminalizeFailed(TaskRunService.Claim claim, TaskRunWorkerContext context, String runId, String code,
            boolean retryable) {
        try {
            execution.failRun(runId);
        } catch (RuntimeException domainFailure) {
            log.warn("Concept Factory run failure transition failed runId={} type={}", runId, domainFailure.getClass().getSimpleName());
        }
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            taskFailureFamily(code), code, retryable);
        publish(context, "FAILED", "job.concept.run.failed", JobEvent.Status.FAILED,
            Map.of("retryable", retryable), code);
    }

    private ConceptAttemptError classify(ExecutionFailure failure) {
        if (isRequestContract(failure)) return ConceptAttemptError.REQUEST_CONTRACT_INVALID;
        if (isSchema(failure)) return ConceptAttemptError.SCHEMA_INVALID;
        return failure.retryable() ? ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE
            : ConceptAttemptError.PERMANENT_PROVIDER_FAILURE;
    }

    private ConceptAttemptError classifyLegal(ExecutionFailure failure) {
        if (isRequestContract(failure)) return ConceptAttemptError.REQUEST_CONTRACT_INVALID;
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

    private boolean isRequestContract(ExecutionFailure failure) {
        return Set.of("INVALID_REQUEST", "UNSUPPORTED_CONTRACT_VERSION", "UNSUPPORTED_TASK_TYPE",
            "UNSUPPORTED_TASK_SCHEMA_VERSION").contains(failure.code());
    }

    private String safeAttemptCode(ConceptAttemptError classification, ExecutionFailure failure) {
        return classification == ConceptAttemptError.REQUEST_CONTRACT_INVALID
            ? ConceptAttemptError.REQUEST_CONTRACT_INVALID.name() : failure.reason();
    }

    private boolean isRunGlobalProviderConfiguration(ExecutionFailure failure) {
        return "UNAUTHORIZED_INTERNAL_CALL".equals(failure.code())
            || Set.of("AI_CONFIGURATION_INVALID", "LEGAL_CONFIGURATION_INVALID",
                "SERVICE_TOKEN_MISSING", "SERVICE_TOKEN_INVALID", "INTERNAL_PRINCIPAL_FORBIDDEN")
                .contains(failure.reason());
    }

    private void publishSlot(TaskRunWorkerContext context, SlotWork slot, String key) {
        publish(context, "SLOT_" + slot.slotNumber(), key, JobEvent.Status.RUNNING,
            Map.of("slot", slot.slotNumber(), "slotNumber", slot.slotNumber(),
                "slotId", slot.slotId(), "variationFocus", slot.focus().name(),
                "runId", context.subjectId(), "correlationId", context.correlationId(),
                "event", eventName(key)), null);
    }

    private void publishRejection(TaskRunWorkerContext context, SlotWork slot, String attemptId,
            ConceptAttemptError reason) {
        var trace = execution.attemptTrace(attemptId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("slot", slot.slotNumber());
        params.put("slotNumber", slot.slotNumber());
        params.put("slotId", slot.slotId());
        params.put("variationFocus", slot.focus().name());
        params.put("runId", context.subjectId());
        params.put("phase", trace == null ? "UNKNOWN" : trace.phase());
        params.put("taskType", rejectionTaskType(trace == null ? null : trace.phase()));
        params.put("attemptNumber", trace == null ? 0 : trace.attemptNumber());
        params.put("conceptAttemptId", attemptId);
        params.put("correlationId", context.correlationId());
        params.put("event", "REJECTED");
        String classification = trace == null || trace.errorClassification() == null
            ? reason.name() : trace.errorClassification();
        String safeCode = trace == null || trace.safeErrorCode() == null
            ? reason.name() : trace.safeErrorCode();
        params.put("errorClassification", classification);
        params.put("safeErrorCode", safeCode);
        params.put("safeReason", safeCode);
        if (safeCode.contains(":")) params.put("failedField", safeCode.substring(safeCode.indexOf(':') + 1));
        params.put("retryable", false);
        publish(context, "SLOT_" + slot.slotNumber(), "job.concept.slot.rejected",
            JobEvent.Status.RUNNING, params, reason.name());
    }

    private String rejectionTaskType(String phase) {
        if (ConceptAttemptPhase.LEGAL_REVIEW.name().equals(phase)) return TaskType.CONCEPT_LEGAL_REVIEW.name();
        if (ConceptAttemptPhase.REDESIGN.name().equals(phase)) return TaskType.CONCEPT_REDESIGN.name();
        return TaskType.CONCEPT_CANDIDATE.name();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
        if (trace != null && trace.errorClassification() != null) {
            params.put("errorClassification", trace.errorClassification());
        }
        if (failure != null) {
            params.put("safeErrorCode", trace != null && trace.safeErrorCode() != null
                ? trace.safeErrorCode() : failure.code());
            params.put("safeReason", failure.reason());
            params.put("retryable", trace != null ? trace.retryable() : failure.retryable());
            if (!failure.validationFields().isEmpty()) {
                params.put("failedField", failure.validationFields().get(0).path());
            }
        } else if (trace != null && trace.safeErrorCode() != null) {
            params.put("safeErrorCode", trace.safeErrorCode());
            params.put("safeReason", trace.safeErrorCode());
            if (trace.safeErrorCode().contains(":")) {
                params.put("failedField", trace.safeErrorCode().substring(trace.safeErrorCode().indexOf(':') + 1));
            }
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
            case "REQUEST_CONTRACT_INVALID" -> "EXECUTION_FAILED";
            case "MODEL_DEPENDENCY_UNAVAILABLE", "DEPENDENCY_RATE_LIMITED",
                 "REQUEST_DEADLINE_EXCEEDED", "TRANSIENT_EXECUTION_FAILURE",
                 "LEGAL_SOURCE_DEPENDENCY_UNAVAILABLE", "MOLEG_DEPENDENCY_UNAVAILABLE",
                 "MOLEG_RATE_LIMITED", "TRANSIENT_PROVIDER_FAILURE" -> "DEPENDENCY_UNAVAILABLE";
            default -> "EXECUTION_FAILED";
        };
    }

    enum WorkerOutcome { COMPLETED, NEEDS_INPUT, RETRY_LATER, FAILED, FATAL_FAILURE }
    enum SlotOutcome { ELIGIBLE, NEEDS_INPUT, RETRY_LATER, FAILED, FATAL_FAILURE }
    private enum GenerationOutcome {
        SUCCESS, DOMAIN_FAILURE, TRANSIENT_PROVIDER_FAILURE, PERMANENT_PROVIDER_FAILURE,
        REQUEST_CONTRACT_FAILURE, RUN_FATAL_PROVIDER_FAILURE
    }
    private enum ReviewOutcome {
        DISPOSITION, REPLACE, TRANSIENT_PROVIDER_FAILURE, PERMANENT_PROVIDER_FAILURE,
        REQUEST_CONTRACT_FAILURE, RUN_FATAL_PROVIDER_FAILURE
    }
    private record AiCall(String attemptId, JsonNode result, ExecutionFailure failure, long durationMs,
                          TaskRunWorkerContext context, TaskType taskType) {}
    private record Generation(GenerationOutcome outcome, String attemptId, JsonNode result,
                              ConceptAttemptError failureClassification, long durationMs) {}
    private record Review(ReviewOutcome outcome, LegalDisposition disposition, JsonNode legal, String attemptId,
                          ConceptAttemptError failureClassification, long durationMs) {}
}
