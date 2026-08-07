package com.aivle.backend.pipeline.concept.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.LegalDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.SlotWork;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.Work;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import com.aivle.backend.pipeline.concept.domain.ConceptAttemptPhase;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
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
            eq(ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE), eq(true));
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
    }

    @Test
    void exhaustedTransientRetryUsesReplacementAndSucceeds() {
        Harness h = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        when(h.ai.execute(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            TaskType type = invocation.getArgument(0);
            if (type == TaskType.CONCEPT_CANDIDATE && candidates.getAndIncrement() < 2) throw transientFailure();
            return type == TaskType.CONCEPT_LEGAL_REVIEW ? h.legal() : h.candidate();
        });
        when(h.execution.legal(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(h.worker.processSlot(h.context, h.work(1), h.slot(1)))
            .isEqualTo(ConceptFactoryWorker.SlotOutcome.ELIGIBLE);
        verify(h.execution).beginReplacement("run", "slot-1", 1);
    }

    @Test
    void schemaRepairSucceedsWithoutReplacement() {
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
        verify(h.execution).beginAttempt("slot-1", ConceptAttemptPhase.REPAIR, "task");
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
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
            ConceptAttemptError.INTERNAL_EXECUTION_ERROR, false, false);
        verify(h.execution).recordAttemptError(eq("run"), eq("slot-1"), anyString(),
            eq(ConceptAttemptError.INTERNAL_EXECUTION_ERROR), eq(false));
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
        verify(h.execution).failSlot(eq("run"), eq("slot-1"), anyString(),
            eq(ConceptAttemptError.PERMANENT_PROVIDER_FAILURE), eq(false), eq(true));
        verify(h.execution, never()).beginReplacement(anyString(), anyString(), anyInt());
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
            eq("PROVIDER_RESPONSE_SCHEMA_REJECTED"), eq(true));
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
        final AtomicInteger attemptSequence = new AtomicInteger();
        final ConceptFactoryWorker worker = new ConceptFactoryWorker(tasks, execution, ai, events, mapper);
        final TaskRunWorkerContext context = new TaskRunWorkerContext("task", 1L, 2L, TaskType.CONCEPT_FACTORY_RUN,
            "CONCEPT_FACTORY_RUN", "run", "{}", "sha256:" + "a".repeat(64), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 1);

        Harness() {
            when(execution.beginAttempt(anyString(), any(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) + "-attempt-" + attemptSequence.incrementAndGet());
            when(execution.beginRetryAttempt(anyString(), any(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) + "-retry-" + attemptSequence.incrementAndGet());
            when(execution.beginLegalReviewAttempt(anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(0) + "-legal-" + attemptSequence.incrementAndGet());
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
            return new Work("run", 1L, "brief", List.of(Map.of("fieldKey", "problem", "value", "x")),
                Map.of("officialEvidence", List.of(Map.of("referenceIndex", 0))), slots);
        }

        JsonNode candidate() {
            return mapper.readTree("{\"conceptName\":\"concept\",\"targetSegment\":\"target\",\"valueProposition\":\"value\",\"solutionMechanism\":\"solution\"}");
        }

        JsonNode legal() {
            return mapper.readTree("{\"status\":\"IMPLEMENTABLE\",\"requiredControls\":[],\"prohibitedVariants\":[]}");
        }
    }
}
