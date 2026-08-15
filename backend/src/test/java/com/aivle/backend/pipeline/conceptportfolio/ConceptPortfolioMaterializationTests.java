package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ConceptPortfolioMaterializationTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConceptPortfolioRunRepository runs = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
    private final ConceptPortfolioContinuationRepository continuations = mock(ConceptPortfolioContinuationRepository.class);
    private final ConceptInputRequestRepository inputs = mock(ConceptInputRequestRepository.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
    private final Project project = mock(Project.class);
    private ConceptPortfolioMaterializationService service;
    private final TaskRunService.Claim claim = new TaskRunService.Claim("task", "attempt", "token");
    private final TaskRunWorkerContext context = new TaskRunWorkerContext(
        "task", 42L, 7L, com.aivle.backend.taskrun.domain.TaskType.CONCEPT_PORTFOLIO_V2_RUN,
        "CONCEPT_PORTFOLIO_RUN", "portfolio", "{}", "sha256:" + "a".repeat(64),
        "key", "correlation", "1.0", "1.0", "ko-KR", 1, 2);

    @BeforeEach
    void setUp() {
        reset(runs, concepts, continuations, inputs, taskRuns, run, project);
        when(runs.findLocked("portfolio")).thenReturn(java.util.Optional.of(run));
        when(run.getProject()).thenReturn(project);
        when(project.getId()).thenReturn(42L);
        when(continuations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ConceptPortfolioMaterializationService(runs, concepts, continuations, inputs,
            new ConceptPortfolioResultContract(), new ConceptPortfolioProductStatusMapper(),
            new ConceptPortfolioJsonHasher(mapper), taskRuns,
            new EffectiveAffectedFieldResolver(mapper), mapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void materializesOneToFiveConceptsWithoutSlotGate(int count) {
        ObjectNode result = acceptedResult(count);
        ConceptPortfolioRunStatus status = service.complete(claim, context, response(result));

        assertThat(status).isEqualTo(ConceptPortfolioRunStatus.RESULTS_AVAILABLE);
        verify(concepts, times(count)).save(any(ConceptPortfolioConcept.class));
        verify(taskRuns).adopt(eq("task"), eq("attempt"), eq("token"), anyString(),
            eq(context.inputHash()), eq("1.0"));
        verify(run).materialize(eq(ConceptPortfolioRunStatus.RESULTS_AVAILABLE), eq(count), eq(0),
            any(), any(), any(), any(), any(), any(), any(), any(), isNull());
    }

    @Test
    void storesOneSharedContextAndFiveCandidateInputArtifacts() {
        ObjectNode result = acceptedResult(0);
        result.put("engineStatus", "FAILED");
        ObjectNode contextNode = result.putObject("continuationContext");
        contextNode.put("contextVersion", "1.0");
        contextNode.putObject("canonicalSeedSnapshot").put("ideaBriefSnapshotId", "brief");
        contextNode.putObject("designSnapshot").put("explorationBreadth", "EXPLORE");
        var plans = contextNode.putArray("plans");
        for (int index = 1; index <= 5; index++) plans.addObject().put("planId", "P" + index);
        var required = result.withArray("requiredInputs");
        var artifacts = result.withArray("continuationArtifacts");
        for (int index = 1; index <= 5; index++) {
            String id = "C" + index;
            ObjectNode input = required.addObject();
            input.put("candidateId", id).put("scope", "CANDIDATE");
            input.putArray("unknownFacts").add("실제 사업 사실");
            input.putArray("affectedFields").add("sellerRole");
            ObjectNode artifact = artifacts.addObject();
            artifact.put("candidateId", id).put("lineageId", "L" + index).put("planId", "P" + index);
        }

        assertThat(service.complete(claim, context, response(result)))
            .isEqualTo(ConceptPortfolioRunStatus.NEEDS_INPUT);
        verify(continuations, times(1)).save(any(ConceptPortfolioContinuation.class));
        verify(inputs, times(5)).save(any(ConceptInputRequest.class));
        verify(taskRuns).adoptNeedsInput(anyString(), anyString(), anyString(), anyString(),
            eq(context.inputHash()), eq("1.0"));
    }

    @Test
    void partialResultsWithCandidateInputStillSucceedTheInitialTask() {
        ObjectNode result = acceptedResult(2);
        ObjectNode shared = result.putObject("continuationContext");
        shared.put("contextVersion", "1.0");
        shared.putArray("plans").addObject().put("planId", "P3");
        ObjectNode input = result.withArray("requiredInputs").addObject();
        input.put("candidateId", "C3").put("scope", "CANDIDATE");
        input.putArray("unknownFacts").add("actual seller");
        input.putArray("affectedFields").add("sellerRole");
        result.withArray("continuationArtifacts").addObject()
            .put("candidateId", "C3").put("lineageId", "L3").put("planId", "P3");

        assertThat(service.complete(claim, context, response(result)))
            .isEqualTo(ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT);
        verify(taskRuns).adopt(anyString(), anyString(), anyString(), anyString(),
            eq(context.inputHash()), eq("1.0"));
        verify(taskRuns, never()).adoptNeedsInput(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString());
        verify(run).materialize(eq(ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT), eq(2), eq(1),
            any(), any(), any(), any(), any(), any(), any(), any(), isNull());
    }

    @Test
    void technicalGlobalFailureFailsTaskAndPortfolioRun() {
        ObjectNode result = acceptedResult(2);
        result.put("engineStatus", "FAILED");
        result.putObject("runSummary").put("failureCode", "RESULT_SCHEMA_INVALID");

        assertThat(service.complete(claim, context, response(result)))
            .isEqualTo(ConceptPortfolioRunStatus.FAILED);
        verify(taskRuns).fail("task", "attempt", "token", "RESULT_SCHEMA_INVALID",
            "AI_RESULT_INVALID", false);
        verifyNoInteractions(concepts, continuations, inputs);
        verify(run).materialize(eq(ConceptPortfolioRunStatus.FAILED), eq(0), eq(0),
            any(), any(), any(), any(), any(), any(), any(), any(),
            eq("RESULT_SCHEMA_INVALID"));
    }

    @Test
    void staleClaimStopsAllProductMaterialization() {
        doThrow(new TaskRunFailure("AI_RESULT_INVALID", "STALE_CLAIM", HttpStatus.CONFLICT, false))
            .when(taskRuns).assertActiveClaim(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.complete(claim, context, response(acceptedResult(2))))
            .isInstanceOf(TaskRunFailure.class);
        verifyNoInteractions(concepts, continuations, inputs);
        verify(run, never()).materialize(any(), anyInt(), anyInt(), any(), any(), any(), any(),
            any(), any(), any(), any(), any());
    }

    private ExecutionResponse response(ObjectNode result) {
        return new ExecutionResponse("1.0", "CONCEPT_PORTFOLIO_V2_RUN", "1.0", "task", "attempt",
            "correlation", context.inputHash(), "1.0", result, mapper.createArrayNode(),
            mapper.createArrayNode(), null);
    }

    private ObjectNode acceptedResult(int count) {
        ObjectNode result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-production-result-v1");
        result.put("contractVersion", "1.0"); result.put("schemaVersion", "1.0");
        result.put("engineRunId", "engine"); result.put("engineStatus", count == 5 ? "READY_FULL" : "READY_LIMITED");
        result.put("runtimeStage", "READY"); result.put("requestedMaxConcepts", 5);
        result.put("producedConceptCount", count); result.put("downstreamReadiness", "PENDING_HYPOTHESIS_CONFIRMATION");
        result.putNull("engineDefaultConceptId"); result.putNull("userSelectedConceptId");
        var conceptValues = result.putArray("concepts");
        var legal = result.putArray("legalSummaries");
        for (int index = 1; index <= count; index++) {
            String id = "C" + index;
            ObjectNode envelope = conceptValues.addObject();
            envelope.put("candidateId", id).put("lineageId", "L" + index).put("planId", "P" + index)
                .putNull("parentCandidateId");
            envelope.putObject("candidate").put("conceptName", "Concept " + index)
                .put("conceptDefinition", "Summary " + index);
            legal.addObject().put("candidateId", id).put("route", "ACCEPT");
        }
        result.putArray("legalResolutions"); result.putArray("requiredInputs");
        result.putArray("preLegalExclusions"); result.putNull("runSummary");
        result.putNull("continuationContext"); result.putArray("continuationArtifacts");
        result.putObject("traceSummary").put("eventCount", 1);
        return result;
    }
}
