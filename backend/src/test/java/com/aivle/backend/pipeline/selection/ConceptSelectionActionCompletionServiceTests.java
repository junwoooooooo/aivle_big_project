package com.aivle.backend.pipeline.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionActionCompletionService;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionActionCompletionService.Outcome;
import com.aivle.backend.pipeline.selection.domain.*;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ConceptSelectionActionCompletionServiceTests {
    private static final String HASH = "sha256:" + "c".repeat(64);

    @Test
    void alternativeSuccessRejectsOldProposalOnlyWhenNewVersionIsReady() {
        Harness h = new Harness(TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE, HypothesisType.CHANNELS,
            "\"직접 영업\"", """
                {"projectId":41,"selectionId":99,"conceptId":"concept-1","candidateHash":"%s",
                 "hypothesisType":"CHANNELS","currentDecisionId":"%s","expectedProposalVersion":1}
                """);
        ExecutionResponse response = h.response("""
            {"hypothesisType":"CHANNELS","proposedValue":"파트너 추천","proposalVersion":2}
            """);

        Outcome outcome = h.service.complete(h.claim, h.context(), response);

        assertThat(outcome).isEqualTo(Outcome.SUCCEEDED);
        assertThat(h.current.getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.REJECTED);
        assertThat(h.selection.getActionStatus()).isEqualTo("SUCCEEDED");
        ArgumentCaptor<ConceptHypothesisDecision> saved = ArgumentCaptor.forClass(ConceptHypothesisDecision.class);
        verify(h.decisions).save(saved.capture());
        assertThat(saved.getValue().getProposalVersion()).isEqualTo(2);
        assertThat(saved.getValue().getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.ALTERNATIVE_PROPOSED);
        verify(h.taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token-1"),
            anyString(), eq(HASH), eq("1.0"));
    }

    @Test
    void legalIneligibleSucceedsTaskExecutionButDoesNotAcceptDecision() {
        Harness h = new Harness(TaskType.CONCEPT_DELTA_LEGAL_REVIEW, HypothesisType.REVENUE_MODEL,
            "\"월 구독\"", """
                {"projectId":41,"selectionId":99,"conceptId":"concept-1","candidateHash":"%s",
                 "hypothesisType":"REVENUE_MODEL","currentDecisionId":"%s","expectedProposalVersion":1,
                 "requestedFinalValue":"거래 수수료","userEdited":true}
                """);
        ExecutionResponse response = h.response("""
            {"status":"REJECTED","safeUserSummary":"현재 조건으로 구현하기 어렵습니다.","officialEvidence":[]}
            """);

        Outcome outcome = h.service.complete(h.claim, h.context(), response);

        assertThat(outcome).isEqualTo(Outcome.LEGAL_INELIGIBLE);
        assertThat(h.current.accepted()).isFalse();
        assertThat(h.current.getFinalValueJson()).isNull();
        assertThat(h.current.getLegalReviewStatus()).isEqualTo(HypothesisLegalReviewStatus.FAILED);
        assertThat(h.selection.getActionStatus()).isEqualTo("LEGAL_INELIGIBLE");
        verify(h.taskRuns).adopt(anyString(), anyString(), anyString(), anyString(), eq(HASH), eq("1.0"));
        verify(h.taskRuns, never()).fail(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void staleHashCannotOverwriteCurrentDecision() {
        Harness h = new Harness(TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE, HypothesisType.CHANNELS,
            "\"직접 영업\"", """
                {"projectId":41,"selectionId":99,"conceptId":"concept-1","candidateHash":"%s",
                 "hypothesisType":"CHANNELS","currentDecisionId":"%s","expectedProposalVersion":1}
                """);
        Concept changedConcept = mock(Concept.class);
        when(changedConcept.getCanonicalHash()).thenReturn("sha256:" + "d".repeat(64));
        when(h.concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 41L))
            .thenReturn(Optional.of(changedConcept));

        Outcome outcome = h.service.complete(h.claim, h.context(), h.response("""
            {"hypothesisType":"CHANNELS","proposedValue":"파트너 추천","proposalVersion":2}
            """));

        assertThat(outcome).isEqualTo(Outcome.STALE);
        assertThat(h.current.getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.PROPOSED);
        assertThat(h.selection.getActionStatus()).isEqualTo("STALE_ACTION_RESULT");
        verify(h.taskRuns).fail("task-1", "attempt-1", "token-1",
            "EXECUTION_FAILED", "STALE_ACTION_RESULT", false);
        verify(h.decisions, never()).save(any());
    }

    @Test
    void providerFailureLeavesOldProposalAndClearsPendingForRetry() {
        Harness h = new Harness(TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE, HypothesisType.CHANNELS,
            "\"직접 영업\"", """
                {"projectId":41,"selectionId":99,"conceptId":"concept-1","candidateHash":"%s",
                 "hypothesisType":"CHANNELS","currentDecisionId":"%s","expectedProposalVersion":1}
                """);

        h.service.fail(h.claim, h.context(), "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);

        assertThat(h.current.getDecisionStatus()).isEqualTo(HypothesisDecisionStatus.PROPOSED);
        assertThat(h.selection.getActionStatus()).isEqualTo("FAILED");
        assertThat(h.selection.getActiveActionTaskRunId()).isNull();
        verify(h.taskRuns).fail("task-1", "attempt-1", "token-1",
            "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", true);
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        final ConceptHypothesisDecisionRepository decisions = mock(ConceptHypothesisDecisionRepository.class);
        final ConceptRepository concepts = mock(ConceptRepository.class);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final ConceptSelectionActionCompletionService service = new ConceptSelectionActionCompletionService(
            selections, decisions, concepts, taskRuns, mapper, new LegalJurisdictionResolver());
        final ConceptSelection selection = ConceptSelection.select(41L, "concept-1", "선택 이유",
            "sha256:" + "a".repeat(64), 7L, Instant.parse("2026-08-08T00:00:00Z"));
        final ConceptHypothesisDecision current;
        final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
        final TaskType taskType;
        final String input;

        Harness(TaskType taskType, HypothesisType hypothesisType, String proposedJson, String inputTemplate) {
            this.taskType = taskType;
            ReflectionTestUtils.setField(selection, "id", 99L);
            current = ConceptHypothesisDecision.initial(selection, hypothesisType, proposedJson,
                "AI_HYPOTHESIS", false, 7L, Instant.now());
            String action = taskType == TaskType.CONCEPT_HYPOTHESIS_ALTERNATIVE
                ? "REQUEST_ALTERNATIVE" : "EDIT_AND_ACCEPT";
            selection.queueAction("task-1", action, hypothesisType, current.getId(), 1);
            input = inputTemplate.formatted(HASH, current.getId());
            when(selections.findByIdAndProjectIdAndDeletedAtIsNull(99L, 41L)).thenReturn(Optional.of(selection));
            when(decisions.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                99L, hypothesisType)).thenReturn(Optional.of(current));
            Concept concept = mock(Concept.class);
            when(concept.getCanonicalHash()).thenReturn(HASH);
            when(concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 41L))
                .thenReturn(Optional.of(concept));
            when(decisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        }

        TaskRunWorkerContext context() {
            return new TaskRunWorkerContext("task-1", 41L, 7L, taskType, "CONCEPT_SELECTION", "99",
                input, HASH, "command-1", "request-1", "1.0", "1.0", "ko-KR", 1, 1);
        }

        ExecutionResponse response(String resultJson) {
            ExecutionResponse response = mock(ExecutionResponse.class);
            when(response.result()).thenReturn(mapper.readTree(resultJson));
            when(response.canonicalInputHash()).thenReturn(HASH);
            when(response.resultSchemaVersion()).thenReturn("1.0");
            return response;
        }
    }
}
