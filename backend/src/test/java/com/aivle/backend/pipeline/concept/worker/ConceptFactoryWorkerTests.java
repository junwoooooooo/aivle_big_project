package com.aivle.backend.pipeline.concept.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.LegalDisposition;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.SlotWork;
import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService.Work;
import com.aivle.backend.pipeline.concept.domain.VariationFocus;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptFactoryWorkerTests {
    @Test
    void permanentFailureInOneSlotDoesNotDiscardAnotherSlot() {
        TaskRunService tasks = mock(TaskRunService.class);
        ConceptFactoryExecutionService execution = mock(ConceptFactoryExecutionService.class);
        ConceptFactoryAiGateway ai = mock(ConceptFactoryAiGateway.class);
        JobEventPublisher events = mock(JobEventPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        ConceptFactoryWorker worker = new ConceptFactoryWorker(tasks, execution, ai, events, mapper);
        TaskRunWorkerContext context = new TaskRunWorkerContext("task", 1L, 2L, TaskType.CONCEPT_FACTORY_RUN,
            "CONCEPT_FACTORY_RUN", "run", "{}", "sha256:" + "a".repeat(64), "key", "correlation",
            "1.0", "1.0", "ko-KR", 1, 1);
        SlotWork failed = new SlotWork("slot-1", 1, VariationFocus.CUSTOMER_EXPERIENCE, 0);
        SlotWork healthy = new SlotWork("slot-2", 2, VariationFocus.REVENUE_AND_PRICING, 0);
        Work work = new Work("run", 1L, "brief", List.of(Map.of("fieldKey", "problem", "value", "x")),
            Map.of("officialEvidence", List.of(Map.of("referenceIndex", 0))), List.of(failed, healthy));
        when(execution.beginAttempt(eq("slot-1"), any(), any())).thenReturn("attempt-1");
        when(execution.beginAttempt(eq("slot-2"), any(), any())).thenReturn("attempt-2");
        when(ai.execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), eq("attempt-1")))
            .thenThrow(new ExecutionFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", false));
        when(ai.execute(eq(TaskType.CONCEPT_CANDIDATE), anyString(), anyString(), eq("attempt-2")))
            .thenReturn(mapper.readTree("{\"conceptName\":\"ok\"}"));
        when(ai.execute(eq(TaskType.CONCEPT_LEGAL_REVIEW), anyString(), anyString(), eq("attempt-2")))
            .thenReturn(mapper.readTree("{\"status\":\"IMPLEMENTABLE\"}"));
        when(execution.legal(eq("run"), eq("slot-2"), eq("attempt-2"), any(), any()))
            .thenReturn(LegalDisposition.ELIGIBLE);

        assertThat(worker.processSlots(context, work)).isTrue();

        verify(execution).failSlot("run", "slot-1", "attempt-1",
            com.aivle.backend.pipeline.concept.domain.ConceptAttemptError.PERMANENT_PROVIDER_FAILURE, false, true);
        verify(execution).generated(eq("slot-2"), eq("attempt-2"), any());
        verify(execution).legal(eq("run"), eq("slot-2"), eq("attempt-2"), any(), any());
    }
}
