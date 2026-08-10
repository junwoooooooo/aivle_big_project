package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioContinuationResultContract.ContractViolation;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ConceptPortfolioContinuationMaterializationTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConceptPortfolioRunRepository runs = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
    private final ConceptInputRequestRepository inputs = mock(ConceptInputRequestRepository.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
    private final Project project = mock(Project.class);
    private final ConceptPortfolioContinuation continuation = mock(ConceptPortfolioContinuation.class);
    private ConceptInputRequest request;
    private ConceptPortfolioContinuationMaterializationService service;
    private final TaskRunService.Claim claim = new TaskRunService.Claim("task", "attempt", "token");
    private final TaskRunWorkerContext context = new TaskRunWorkerContext(
        "task", 42L, 7L, TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE,
        "CONCEPT_PORTFOLIO_RUN", "run", "{}", "sha256:" + "a".repeat(64),
        "key", "correlation", "1.0", "1.0", "ko-KR", 1, 2);

    @BeforeEach
    void setUp() {
        reset(runs, concepts, inputs, taskRuns, run, project, continuation);
        when(run.getId()).thenReturn("run"); when(run.getProject()).thenReturn(project);
        when(run.getActiveTaskRunId()).thenReturn("task"); when(project.getId()).thenReturn(42L);
        request = ConceptInputRequest.open(run, continuation, "candidate", "lineage", "CANDIDATE",
            "질문", null, null, "요약", "[\"사실\"]", "[\"sellerRole\"]",
            "{\"candidateId\":\"candidate\"}", "sha256:" + "b".repeat(64));
        request.answer("task", LocalDateTime.parse("2026-08-10T00:00:00"));
        when(runs.findLocked("run")).thenReturn(Optional.of(run));
        when(inputs.findLocked(request.getId())).thenReturn(Optional.of(request));
        service = new ConceptPortfolioContinuationMaterializationService(runs, concepts, inputs,
            new ConceptPortfolioContinuationResultContract(), new ConceptPortfolioJsonHasher(mapper),
            taskRuns, mapper, Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void mergesRecoveredCandidateAfterTwoExistingConceptsAndResolvesOldRequest() {
        ConceptPortfolioConcept previous = mock(ConceptPortfolioConcept.class);
        when(previous.getDisplayOrder()).thenReturn(2);
        when(concepts.findFirstByRunIdAndDeletedAtIsNullOrderByDisplayOrderDesc("run"))
            .thenReturn(Optional.of(previous));
        when(concepts.countByRunIdAndDeletedAtIsNull("run")).thenReturn(3L);
        when(inputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("run"), any())).thenReturn(0L);

        assertThat(service.complete(claim, context, response(result("ACCEPTED"))))
            .isEqualTo(ConceptPortfolioContinuationOutcome.ACCEPTED);

        verify(concepts).save(argThat(value -> value.getDisplayOrder() == 3
            && value.getLineageId().equals("lineage") && value.isSelectable()));
        verify(concepts, never()).delete(any());
        assertThat(request.getStatus()).isEqualTo(ConceptInputRequestStatus.RESOLVED);
        verify(taskRuns).adopt(eq("task"), eq("attempt"), eq("token"), anyString(),
            eq(context.inputHash()), eq("1.0"));
        verify(run).completeContinuation(ConceptPortfolioRunStatus.RESULTS_AVAILABLE, 3, 0, null);
    }

    @Test
    void needsInputResolvesOldQuestionCreatesNewOpenQuestionAndPreservesResults() {
        when(concepts.countByRunIdAndDeletedAtIsNull("run")).thenReturn(2L);
        when(inputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("run"), any())).thenReturn(1L);
        when(inputs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.complete(claim, context, response(result("NEEDS_INPUT"))))
            .isEqualTo(ConceptPortfolioContinuationOutcome.NEEDS_INPUT);

        assertThat(request.getStatus()).isEqualTo(ConceptInputRequestStatus.RESOLVED);
        verify(inputs).save(argThat(value -> value.getStatus() == ConceptInputRequestStatus.OPEN
            && value.getCandidateId().equals("candidate")
            && value.getContinuation() == continuation));
        verify(taskRuns).adoptNeedsInput(anyString(), anyString(), anyString(), anyString(),
            eq(context.inputHash()), eq("1.0"));
        verify(run).completeContinuation(ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT, 2, 1, null);
    }

    @Test
    void zeroConceptFollowUpAndAllExcludedHaveDistinctProductMeaning() {
        when(concepts.countByRunIdAndDeletedAtIsNull("run")).thenReturn(0L);
        when(inputs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("run"), any())).thenReturn(1L);
        service.complete(claim, context, response(result("NEEDS_INPUT")));
        verify(run).completeContinuation(ConceptPortfolioRunStatus.NEEDS_INPUT, 0, 1, null);

        setUp();
        when(concepts.countByRunIdAndDeletedAtIsNull("run")).thenReturn(0L);
        when(inputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("run"), any())).thenReturn(0L);
        service.complete(claim, context, response(result("EXCLUDED")));
        verify(run).completeContinuation(ConceptPortfolioRunStatus.FAILED, 0, 0,
            "NO_ACCEPTED_CONCEPTS");
    }

    @Test
    void systemFailureKeepsAnsweredRequestAndExistingPortfolioForRetry() {
        when(concepts.countByRunIdAndDeletedAtIsNull("run")).thenReturn(2L);
        when(inputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("run"), any())).thenReturn(0L);

        assertThat(service.complete(claim, context, response(result("SYSTEM_FAILURE"))))
            .isEqualTo(ConceptPortfolioContinuationOutcome.SYSTEM_FAILURE);

        assertThat(request.getStatus()).isEqualTo(ConceptInputRequestStatus.ANSWERED);
        verify(taskRuns).fail("task", "attempt", "token", "DEPENDENCY_UNAVAILABLE",
            "PERMANENT_EXECUTION_FAILURE", false);
        verify(concepts, never()).save(any()); verify(inputs, never()).save(any());
        verify(run).continuationFailed(2, 0);
    }

    @Test
    void sameLineageAndStaleClaimCannotMutatePortfolio() {
        when(concepts.existsByRunIdAndLineageIdAndDeletedAtIsNull("run", "lineage"))
            .thenReturn(true);
        assertThatThrownBy(() -> service.complete(claim, context, response(result("ACCEPTED"))))
            .isInstanceOf(ContractViolation.class);
        verify(concepts, never()).save(any());

        setUp();
        doThrow(new TaskRunFailure("AI_RESULT_INVALID", "STALE_CLAIM",
            org.springframework.http.HttpStatus.CONFLICT, false)).when(taskRuns)
            .assertActiveClaim("task", "attempt", "token");
        assertThatThrownBy(() -> service.complete(claim, context, response(result("NEEDS_INPUT"))))
            .isInstanceOf(TaskRunFailure.class);
        assertThat(request.getStatus()).isEqualTo(ConceptInputRequestStatus.ANSWERED);
        verifyNoInteractions(concepts);
        verify(inputs, never()).save(any());
        verify(run, never()).completeContinuation(any(), anyInt(), anyInt(), any());
    }

    private ExecutionResponse response(ObjectNode result) {
        return new ExecutionResponse("1.0", "CONCEPT_PORTFOLIO_V2_CONTINUE", "1.0", "task",
            "attempt", "correlation", context.inputHash(), "1.0", result,
            mapper.createArrayNode(), mapper.createArrayNode(), null);
    }

    private ObjectNode result(String outcome) {
        ObjectNode result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-continuation-result-v1");
        result.put("contractVersion", "1.0"); result.put("schemaVersion", "1.0");
        result.put("inputRequestId", request.getId()); result.put("candidateId", "candidate");
        result.put("lineageId", "lineage"); result.put("outcome", outcome);
        result.putNull("candidate"); result.putNull("legalReview"); result.putNull("requiredInput");
        result.putNull("continuationArtifact"); result.putNull("exclusionReason");
        result.putNull("failureCode"); result.putObject("traceSummary").put("eventCount", 1);
        if ("ACCEPTED".equals(outcome)) {
            ObjectNode envelope = result.putObject("candidate");
            envelope.put("candidateId", "candidate").put("lineageId", "lineage")
                .put("planId", "plan").putNull("parentCandidateId");
            envelope.putObject("candidate").put("conceptName", "Recovered")
                .put("conceptDefinition", "Recovered summary");
            result.putObject("legalReview").put("candidateId", "candidate").put("route", "ACCEPT");
        } else if ("NEEDS_INPUT".equals(outcome)) {
            ObjectNode required = result.putObject("requiredInput");
            required.put("candidateId", "candidate").put("scope", "CANDIDATE")
                .put("question", "추가 질문").put("safeSummary", "추가 확인");
            required.putArray("unknownFacts").add("추가 사실");
            required.putArray("affectedFields").add("sellerRole");
            result.putObject("continuationArtifact").put("candidateId", "candidate")
                .put("lineageId", "lineage").put("planId", "plan");
        } else if ("EXCLUDED".equals(outcome)) {
            result.put("exclusionReason", "Candidate validation 제외");
        } else {
            result.put("failureCode", "DEPENDENCY_UNAVAILABLE");
        }
        return result;
    }
}
