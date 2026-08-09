package com.aivle.backend.pipeline.concept.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.LegalDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.CandidateDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.SlotWork;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.Work;
import com.aivle.backend.pipeline.concept.application.ConceptLegalFactPatternMapper;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase;
import com.aivle.backend.pipeline.concept.domain.ConceptGenerationStrategy;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ValidationIssue;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConceptFactoryWorkerTests {
    @Test
    void fiveSlotsAllSucceed() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlots(h.context, h.work(5)))
            .isEqualTo(ConceptFactoryWorker.WorkerOutcome.COMPLETED);
        verify(h.execution, times(5)).generated(anyString(), anyString(), any());
        verify(h.execution, times(5)).recordCandidateInspection("run");
    }

    @Test
    void transientProviderFailureRetriesSameSlotOnceThenSucceeds() {
        Harness h = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_CANDIDATE && candidates.getAndIncrement() == 0) throw transientFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution).recordAttemptError(eq("run"), eq("slot-1"), anyString(),
            eq(ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE), eq("MODEL_DEPENDENCY_UNAVAILABLE"), eq(true));
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
    }

    @Test
    void transientRetriesAreBoundedPerCallRatherThanOncePerRun() {
        Harness h = new Harness();
        Map<String, AtomicInteger> calls = new java.util.concurrent.ConcurrentHashMap<>();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            String attempt = invocation.getArgument(3);
            if (type == TaskType.CONCEPT_CANDIDATE) {
                String slot = attempt.substring(0, attempt.indexOf('-', 5));
                if (calls.computeIfAbsent(slot, ignored -> new AtomicInteger()).getAndIncrement() == 0) {
                    throw transientFailure();
                }
            }
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlots(h.context, h.work(3)))
            .isEqualTo(ConceptFactoryWorker.WorkerOutcome.COMPLETED);
        verify(h.execution, times(3)).recordProviderTransientRetry("run");
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
    }

    @Test
    void exhaustedTransientRetryStopsRunWithoutReplacementOrRemainingSlotCalls() {
        Harness h = new Harness();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_CANDIDATE) throw transientFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });

        assertThat(h.worker.processSlots(h.context, h.work(5)))
            .isEqualTo(ConceptFactoryWorker.WorkerOutcome.RETRY_LATER);
        verify(h.ai, times(3)).execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), anyString());
        verify(h.execution, times(2)).recordProviderTransientRetry("run");
        verify(h.execution, never()).recordCandidateInspection(anyString());
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
        verify(h.execution, never()).beginAttempt(eq("slot-2"), any(), anyString());
    }

    @Test
    void rateLimitAndDeadlineExhaustionNeverBecomeCandidateReplacement() {
        for (ExecutionFailure failure : List.of(
                new ExecutionFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", true),
                new ExecutionFailure("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", true))) {
            Harness h = new Harness();
            when(h.ai.execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), anyString()))
                .thenThrow(failure);
            assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
                .isEqualTo(ConceptFactoryWorker.SlotOutcome.RETRY_LATER);
            verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
            verify(h.execution, never()).recordCandidateInspection(anyString());
        }
    }

    @Test
    void schemaFailureUsesReplacementInsteadOfBlindRepair() {
        Harness h = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_CANDIDATE && candidates.getAndIncrement() == 0) throw schemaFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution, never()).beginAttempt("slot-1", ConceptAttemptPhase.REPAIR, "task");
        verify(h.execution).beginReplacement("run", "slot-1", 1);
    }

    @Test
    void failedSchemaRepairUsesReplacement() {
        Harness h = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_CANDIDATE && candidates.getAndIncrement() < 2) throw schemaFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution).beginReplacement("run", "slot-1", 1);
    }

    @Test
    void legalRedesignCanBecomeEligible() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN, LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.ai).execute(eq(TaskType.CONCEPT_REDESIGN), anyString(), anyString(), anyString());
        verify(h.execution).beginAttempt("slot-1", ConceptAttemptPhase.REDESIGN, "task");
        verify(h.execution, atLeastOnce()).recordCompletedRedesign(eq("slot-1"), anyString());
    }

    @Test
    void redesignTransientFailureConsumesNoCandidateOrRedesignBudgetAndCanResume() {
        Harness h = new Harness();
        AtomicInteger redesignCalls = new AtomicInteger();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_REDESIGN && redesignCalls.getAndIncrement() < 3) {
                throw transientFailure();
            }
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN, LegalDisposition.REDESIGN, LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.RETRY_LATER);
        verify(h.execution, times(1)).recordCandidateInspection("run");
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());

        SlotWork preserved = new SlotWork("slot-1", 1, VariationFocus.CUSTOMER_EXPERIENCE,
            0, 0, "slot-1-preserved", h.candidate().toString());
        assertThat(h.worker.processSlot(h.context, h.work(1), preserved))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution, times(2)).recordCandidateInspection("run");
        verify(h.execution, times(2)).recordCompletedRedesign(eq("slot-1"), contains("attempt"));
    }

    @Test
    void failedRedesignUsesReplacement() {
        Harness h = new Harness();
        AtomicInteger redesignCalls = new AtomicInteger();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_REDESIGN && redesignCalls.getAndIncrement() < 2) throw schemaFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN, LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution).beginReplacement("run", "slot-1", 1);
    }

    @Test
    void redesignedDuplicateIsRejectedBeforeAnotherLegalCall() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
            .thenReturn(CandidateDisposition.ACCEPTED, CandidateDisposition.DUPLICATE, CandidateDisposition.ACCEPTED);
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN, LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);

        verify(h.execution, times(3)).validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList());
        verify(h.ai, times(2)).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), anyString());
        verify(h.execution).beginReplacement("run", "slot-1", 1);
    }

    @Test
    void ambiguousRedesignUsesSemanticJudgeAndDistinctResultReachesLegal() {
        Harness h = new Harness();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_LEGAL_REVIEW) return h.legal();
            if (type == TaskType.CONCEPT_DISTINCTNESS_JUDGE) return h.distinctness("DISTINCT");
            return h.candidate();
        });
        when(h.execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
            .thenReturn(CandidateDisposition.ACCEPTED, CandidateDisposition.SEMANTIC_REVIEW_REQUIRED);
        when(h.execution.semanticComparisons(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(Map.of("coreValue", "기존 가치")));
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN, LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.ai).execute(eq(TaskType.CONCEPT_DISTINCTNESS_JUDGE), anyString(), anyString(), anyString());
        verify(h.ai, times(2)).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), anyString());
    }

    @Test
    void ambiguousRedesignDuplicateIsReplacedWithoutSendingThatCandidateToLegal() {
        Harness h = new Harness();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_LEGAL_REVIEW) return h.legal();
            if (type == TaskType.CONCEPT_DISTINCTNESS_JUDGE) return h.distinctness("DUPLICATE");
            return h.candidate();
        });
        when(h.execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
            .thenReturn(CandidateDisposition.ACCEPTED, CandidateDisposition.SEMANTIC_REVIEW_REQUIRED,
                CandidateDisposition.ACCEPTED);
        when(h.execution.semanticComparisons(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(Map.of("coreValue", "기존 가치")));
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN, LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution).rejectSemanticDuplicate(eq("slot-1"), anyString(), any());
        verify(h.execution).beginReplacement("run", "slot-1", 1);
        verify(h.ai, times(2)).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), anyString());
    }

    @Test
    void ambiguousRedesignJudgeFailureNeverPassesTheCandidateToLegal() {
        Harness h = new Harness();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_LEGAL_REVIEW) return h.legal();
            if (type == TaskType.CONCEPT_DISTINCTNESS_JUDGE) throw schemaFailure();
            return h.candidate();
        });
        when(h.execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
            .thenReturn(CandidateDisposition.ACCEPTED, CandidateDisposition.SEMANTIC_REVIEW_REQUIRED);
        when(h.execution.semanticComparisons(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of(Map.of("coreValue", "기존 가치")));
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.FAILED);
        verify(h.ai, times(1)).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), anyString());
        verify(h.execution).recordAttemptError(eq("run"), eq("slot-1"), anyString(),
            eq(ConceptAttemptError.SCHEMA_INVALID), eq("PROVIDER_RESPONSE_SCHEMA_REJECTED"), eq(false));
    }

    @Test
    void legalInputContainsOnlyFactPatternHashAndExternalFacts() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);

        var input = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(h.ai).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), input.capture(), anyString(), anyString());
        JsonNode parsed = h.mapper.readTree(input.getValue());
        assertThat(parsed.path("legalFactPattern").path("commercialRoles").path("sellerRole").asText())
            .isEqualTo("예약 사업자");
        assertThat(parsed.path("factPatternHash").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(parsed.path("externalFactContext").has("facts")).isTrue();
        assertThat(input.getValue()).doesNotContain("sharedOfficialEvidence", "preMarketSom");
    }

    @Test
    void needsInputIsExplicitTerminalOutcome() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.NEEDS_INPUT);

        assertThat(h.worker.processSlots(h.context, h.work(1)))
            .isEqualTo(ConceptFactoryWorker.WorkerOutcome.NEEDS_INPUT);
    }

    @Test
    void replacementLimitExhaustionFailsSlotExplicitly() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REPLACE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.FAILED);
        verify(h.execution).failSlot("run", "slot-1", null,
            ConceptAttemptError.LEGAL_REJECTED, false, false);
        verify(h.execution).beginReplacement("run", "slot-1", 1);
        verify(h.execution).beginReplacement("run", "slot-1", 2);
    }

    @Test
    void permanentProviderFailureFailsWithoutReplacement() {
        Harness h = new Harness();
        when(h.ai.execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), anyString()))
            .thenThrow(permanentFailure());

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.FAILED);
        verify(h.execution).failSlot(eq("run"), eq("slot-1"), isNull(),
            eq(ConceptAttemptError.PERMANENT_PROVIDER_FAILURE), eq(false), eq(true));
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
    }

    @Test
    void requestContractFailureIsRunFatalAndNeverCallsRemainingSlots() {
        Harness h = new Harness();
        when(h.execution.attemptTrace(anyString())).thenReturn(
            new ConceptFactoryExecutionService.AttemptTrace(1, "INITIAL",
                "REQUEST_CONTRACT_INVALID", "REQUEST_CONTRACT_INVALID", false));
        when(h.ai.execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), anyString()))
            .thenThrow(new ExecutionFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", false,
                List.of(new ValidationIssue(
                    "input.rejectedConceptFingerprints.0.partnerRequirements",
                    "valid contract value", "extra_forbidden"))));

        assertThat(h.worker.processSlots(h.context, h.work(5)))
            .isEqualTo(ConceptFactoryWorker.WorkerOutcome.FATAL_FAILURE);

        verify(h.ai, times(1)).execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), anyString());
        verify(h.execution).recordAttemptError(eq("run"), eq("slot-1"), anyString(),
            eq(ConceptAttemptError.REQUEST_CONTRACT_INVALID), eq("REQUEST_CONTRACT_INVALID"), eq(false));
        verify(h.execution).failSlot("run", "slot-1", null,
            ConceptAttemptError.REQUEST_CONTRACT_INVALID, false, true);
        verify(h.execution, never()).beginAttempt(eq("slot-2"), any(), anyString());
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
        verify(h.execution, never()).recordCandidateInspection(anyString());
        var event = org.mockito.ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(h.events, atLeastOnce()).publish(event.capture());
        JobEventPublisher.Command failed = event.getAllValues().stream()
            .filter(value -> value.eventType().equals("job.concept.slot.generation_failed"))
            .findFirst().orElseThrow();
        assertThat(failed.messageParams().get("errorClassification")).isEqualTo("REQUEST_CONTRACT_INVALID");
        assertThat(failed.messageParams().get("safeErrorCode")).isEqualTo("REQUEST_CONTRACT_INVALID");
        assertThat(failed.messageParams().get("safeReason")).isEqualTo("FIELD_CONSTRAINT_VIOLATION");
        assertThat(failed.messageParams().get("failedField"))
            .isEqualTo("input.rejectedConceptFingerprints.0.partnerRequirements");
        assertThat(failed.messageParams().get("retryable")).isEqualTo(false);
    }

    @Test
    void requestContractFatalUsesExecutionFailedParentTaxonomy() {
        Harness h = new Harness();
        h.claim();
        when(h.execution.prepare("run", 1L)).thenReturn(h.work(5));
        when(h.execution.failureCode("run")).thenReturn("REQUEST_CONTRACT_INVALID");
        when(h.ai.execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), anyString()))
            .thenThrow(new ExecutionFailure("INVALID_REQUEST", "UNKNOWN_FIELD", false));

        assertThat(h.worker.processOne()).isTrue();

        verify(h.tasks).fail("task", "task-attempt", "claim",
            "EXECUTION_FAILED", "REQUEST_CONTRACT_INVALID", false);
        verify(h.execution, never()).beginAttempt(eq("slot-2"), any(), anyString());
    }

    @Test
    void legalRedesignExhaustionCreatesOneCanonicalDiscardBeforeReplacement() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.REDESIGN);

        SlotWork exhausted = new SlotWork("slot-1", 1, VariationFocus.CUSTOMER_EXPERIENCE,
            0, 2, null, null);
        assertThat(h.worker.processSlot(h.context, h.work(1), exhausted))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.FAILED);

        verify(h.execution, times(1)).discardCandidate(eq("slot-1"), anyString(),
            eq(ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED), anyString());
        verify(h.execution, never()).recordCandidateExhaustion(anyString(), any());
    }

    @Test
    void duplicateIsReplacedBeforeLegalReviewAndTargetSlotCanStillBecomeEligible() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.replacementFeedback(anyString(), anyString(), anyString(), any(), any(), anyInt()))
            .thenReturn(Map.of("round", 1, "mustChangeDimensions",
                List.of("problemScenario", "solutionMechanism")));
        when(h.execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
            .thenReturn(CandidateDisposition.DUPLICATE, CandidateDisposition.ACCEPTED);
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution).beginReplacement("run", "slot-1", 1);
        verify(h.execution, times(2)).recordCandidateInspection("run");
        verify(h.ai, times(1)).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), anyString());
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(h.ai, times(2)).execute(eq(TaskType.CONCEPT_CANDIDATE), input.capture(), anyString(), anyString());
        JsonNode replacementInput = h.mapper.readTree(input.getAllValues().get(1));
        assertThat(replacementInput.path("replacementContext").path("mustChangeDimensions")).hasSize(2);
    }

    @Test
    void duplicateReplacementExhaustionUsesDistinctConceptFailureCode() {
        Harness h = new Harness();
        h.successfulAi();
        when(h.execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
            .thenReturn(CandidateDisposition.DUPLICATE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.FAILED);
        verify(h.execution, never()).recordCandidateExhaustion(anyString(), any());
        verify(h.execution).failSlot("run", "slot-1", null,
            ConceptAttemptError.INSUFFICIENT_DISTINCT_CONCEPTS, false, false);
        verify(h.ai, never()).execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), anyString());
    }

    @Test
    void legalSchemaFailurePreservesCandidateAndDoesNotReplace() {
        Harness h = new Harness();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            if (invocation.getArgument(0) == TaskType.CONCEPT_LEGAL_REVIEW) throw schemaFailure();
            return h.candidate();
        });

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.FAILED);
        verify(h.execution).generated(eq("slot-1"), anyString(), any());
        verify(h.execution).failLegalReview(eq("run"), eq("slot-1"), anyString(),
            eq(ConceptAttemptError.RESULT_SCHEMA_INVALID),
            eq("PROVIDER_RESPONSE_SCHEMA_REJECTED"), eq(false));
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
    }

    @Test
    void failedSlotDoesNotDiscardSuccessfulOtherSlot() {
        Harness h = new Harness();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            String attempt = invocation.getArgument(3);
            if (type == TaskType.CONCEPT_CANDIDATE && attempt.contains("slot-1")) throw permanentFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), eq("slot-2"), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlots(h.context, h.work(2)))
            .isEqualTo(ConceptFactoryWorker.WorkerOutcome.FAILED);
        verify(h.execution).generated(eq("slot-2"), anyString(), any());
        verify(h.execution).legal(eq("run"), eq("slot-2"), anyString(), any(), any());
    }

    @Test
    void eachClaimPathTerminatesParentTaskRun() {
        Harness completed = new Harness();
        completed.claim();
        when(completed.execution.prepare("run", 1L)).thenReturn(completed.work(0));
        when(completed.execution.completeIfEligible("run")).thenReturn(true);
        assertThat(completed.worker.processOne()).isTrue();
        verify(completed.tasks).adopt(eq("task"), eq("task-attempt"), eq("claim"), anyString(), anyString(), eq("1.0"));

        Harness needsInput = new Harness();
        needsInput.claim();
        needsInput.successfulAi();
        when(needsInput.execution.prepare("run", 1L)).thenReturn(needsInput.work(1));
        when(needsInput.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.NEEDS_INPUT);
        assertThat(needsInput.worker.processOne()).isTrue();
        verify(needsInput.tasks).needsInput("task", "task-attempt", "claim");

        Harness failed = new Harness();
        failed.claim();
        when(failed.execution.prepare("run", 1L)).thenThrow(new IllegalStateException("boom"));
        assertThat(failed.worker.processOne()).isTrue();
        verify(failed.tasks).fail("task", "task-attempt", "claim", "EXECUTION_FAILED", "INTERNAL_EXECUTION_ERROR", false);
    }

    private static ExecutionFailure transientFailure() {
        return new ExecutionFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);
    }

    private static ExecutionFailure permanentFailure() {
        return new ExecutionFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", false);
    }

    private static ExecutionFailure schemaFailure() {
        return new ExecutionFailure("RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", false);
    }

    private static final class Harness {
        final TaskRunService tasks = mock(TaskRunService.class);
        final ConceptFactoryExecutionService execution = mock(ConceptFactoryExecutionService.class);
        final ConceptFactoryAiGateway ai = mock(ConceptFactoryAiGateway.class);
        final JobEventPublisher events = mock(JobEventPublisher.class);
        final ObjectMapper mapper = new ObjectMapper();
        final ProviderRetryPolicy retryPolicy = mock(ProviderRetryPolicy.class);
        final AtomicInteger attemptSequence = new AtomicInteger();
        final ConceptFactoryWorker worker = new ConceptFactoryWorker(tasks, execution, ai, events, mapper, retryPolicy);
        final TaskRunWorkerContext context = new TaskRunWorkerContext("task", 1L, 2L, TaskType.CONCEPT_FACTORY_RUN,
            "CONCEPT_FACTORY_RUN", "run", "{}", "sha256:" + "a".repeat(64), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 1);

        Harness() {
            when(retryPolicy.canRetry(anyInt())).thenAnswer(invocation -> invocation.<Integer>getArgument(0) < 2);
            when(execution.validateCandidate(anyString(), anyString(), anyString(), any(), any(), anyInt(), anyList()))
                .thenReturn(CandidateDisposition.ACCEPTED);
            when(execution.beginAttempt(anyString(), any(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) + "-attempt-" + attemptSequence.incrementAndGet());
            when(execution.beginRetryAttempt(anyString(), any(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) + "-retry-" + attemptSequence.incrementAndGet());
            when(execution.beginLegalReviewAttempt(anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) + "-legal-" + attemptSequence.incrementAndGet());
            when(execution.legalFactPattern(any())).thenReturn(new ConceptLegalFactPatternMapper.Result(
                mapper.readTree("{\"schemaVersion\":\"2.0\",\"commercialRoles\":{\"sellerRole\":\"예약 사업자\"},\"paymentFlow\":{\"value\":[\"사업자 구독 결제\"]}}"),
                "sha256:" + "d".repeat(64)));
        }

        void successfulAi() {
            when(ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) == TaskType.CONCEPT_LEGAL_REVIEW ? legal() : candidate());
        }

        void claim() {
            when(tasks.claimNext(eq(TaskType.CONCEPT_FACTORY_RUN), anyString(), any(), any()))
                .thenReturn(new TaskRunService.Claim("task", "task-attempt", "claim"));
            when(tasks.workerContext("task")).thenReturn(context);
        }

        SlotWork slot(int number) {
            return new SlotWork("slot-" + number, number, VariationFocus.values()[number - 1], 0);
        }

        Work work(int count) {
            List<SlotWork> slots = Arrays.stream(VariationFocus.values()).limit(count)
                .map(focus -> slot(focus.ordinal() + 1)).toList();
            return new Work("run", 1L, "brief", ConceptGenerationStrategy.EXPLORE,
                List.of(Map.of("fieldKey", "problem", "value", "x", "source", "USER_INPUT", "authority", "LOCKED")),
                Map.of("sourceSnapshotHash", "sha256:" + "a".repeat(64),
                    "registryVersion", "legal-registry-v1", "facts", List.of()), slots);
        }

        JsonNode candidate() {
            return mapper.readTree("{\"conceptName\":\"concept\",\"targetSegment\":\"target\",\"valueProposition\":\"value\",\"solutionMechanism\":\"solution\"}");
        }

        JsonNode legal() {
            return mapper.readTree("{\"status\":\"IMPLEMENTABLE\",\"requiredControls\":[],\"prohibitedVariants\":[],\"redesignRequirements\":[\"결제 주체를 명시\"]}");
        }

        JsonNode distinctness(String decision) {
            return mapper.readTree("{\"decision\":\"" + decision
                + "\",\"overlappingDimensions\":[],\"materiallyDifferentDimensions\":[\"coreValue\"],"
                + "\"safeSummary\":\"사업 구조를 비교했습니다.\"}");
        }
    }
}
