package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementMaterializationTests {
    private static final String HASH = "sha256:" + "b".repeat(64);
    @Mock ConceptRefinementRoundRepository rounds;
    @Mock BusinessValidationCoordinator validations;
    @Mock TaskRunService taskRuns;
    @Mock ConceptPortfolioSelection selection;
    private final ObjectMapper mapper = new ObjectMapper();
    private ConceptRefinementMaterializationService service;
    private CompletedSource source;
    private ConceptRefinementRound round;
    private TaskRunService.Claim claim;
    private TaskRunWorkerContext context;

    @BeforeEach
    void setUp() {
        service = new ConceptRefinementMaterializationService(rounds, validations, taskRuns, mapper);
        source = new CompletedSource("session-1", 91L, 92L, "seed-1", 31L, 4, 3, HASH);
        round = ConceptRefinementRound.start(41L, source, "task-1", "start-key", HASH);
        claim = new TaskRunService.Claim("task-1", "attempt-1", "claim-1");
        context = new TaskRunWorkerContext("task-1", 41L, 7L,
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "CONCEPT_PORTFOLIO_SELECTION", "31",
            "{}", HASH, "start-key", "request-1", "1.0", "1.0", "ko-KR", 1, 2);
        when(rounds.findByTaskRunIdForUpdate("task-1")).thenReturn(Optional.of(round));
        when(validations.requireCurrentCompletedSource(7L, 41L)).thenReturn(source);
        lenient().when(selection.isCurrent()).thenReturn(true);
        lenient().when(selection.getId()).thenReturn(31L);
        lenient().when(selection.getProjectId()).thenReturn(41L);
        lenient().when(selection.getActiveTaskRunId()).thenReturn("task-1");
        lenient().when(selection.getHypothesisRevision()).thenReturn(4);
    }

    @Test
    void successStoresProposalAwaitsDecisionAndOnlyClearsAuxiliaryTask() {
        JsonNode input = input();
        JsonNode result = result();
        ExecutionResponse response = response(result);

        service.complete(claim, context, response, result, input, selection);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.AWAITING_DECISION);
        assertThat(mapper.readTree(round.getProposalJson())).hasSize(1);
        verify(selection).completeAuxiliaryTask("task-1");
        verify(selection, never()).completeTask(anyString(), any(), anyBoolean());
        verify(selection, never()).failTask(anyString(), any(), anyString());
        verify(taskRuns).adopt("task-1", "attempt-1", "claim-1", mapper.writeValueAsString(result), HASH, "1.0");
    }

    @Test
    void failureMarksRoundFailedAndPreservesSelectionStatus() {
        service.fail(claim, context, "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", false, selection);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.FAILED);
        assertThat(round.getLastErrorCode()).isEqualTo("EXECUTION_FAILED");
        verify(selection).clearAuxiliaryTaskIfActive("task-1");
        verify(selection, never()).failTask(anyString(), any(), anyString());
        verify(taskRuns).fail("task-1", "attempt-1", "claim-1",
            "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", false);
    }

    @Test
    void lateResultFromChangedValidationIsRejectedAndRoundBecomesStale() {
        CompletedSource newer = new CompletedSource("session-2", 101L, 102L, "seed-2", 31L, 4, 4, HASH);
        when(validations.requireCurrentCompletedSource(7L, 41L)).thenReturn(newer);
        JsonNode result = result();

        assertThatThrownBy(() -> service.complete(claim, context, response(result), result, input(), selection))
            .isInstanceOf(ConceptRefinementMaterializationService.StaleResult.class);
        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.STALE);
        verify(taskRuns).rejectAndFail(eq("task-1"), eq("attempt-1"), eq("claim-1"),
            anyString(), eq("1.0"), eq("SOURCE_STALE"));
        verify(taskRuns, never()).adopt(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void responseCurrentValueMustDeepEqualAuthoritativeMaterialBaseline() {
        JsonNode result = result();
        ((tools.jackson.databind.node.ObjectNode) result.path("refinementProposals").path(0))
            .put("currentValue", "8,900원");

        assertThatThrownBy(() -> service.complete(claim, context, response(result), result, input(), selection))
            .isInstanceOf(ConceptRefinementMaterializationService.ContractViolation.class);
        verify(taskRuns, never()).adopt(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private JsonNode input() {
        var input = mapper.createObjectNode();
        input.put("expectedHypothesisRevision", 4);
        var material = input.putObject("refinementMaterial");
        var binding = material.putObject("sourceBinding");
        binding.put("businessValidationSessionId", "session-1"); binding.put("marketVersionId", 91);
        binding.put("bmVersionId", 92); binding.put("marketSeedSnapshotId", "seed-1");
        binding.put("selectionId", 31); binding.put("selectionRevision", 4); binding.put("bmPlanRevision", 3);
        material.putArray("marketEvidence").addObject().put("id", "E-1");
        material.putArray("legalFindings").addObject().put("reference", "법률 제1조");
        material.putObject("currentEditableValues").put("price", "10,000원");
        material.putObject("frozenValues").put("conceptName", "Seed 사업안");
        material.putArray("allowedLegalRefs").add("법률 제1조");
        return input;
    }

    private JsonNode result() {
        var result = mapper.createObjectNode();
        var proposal = result.putArray("refinementProposals").addObject();
        proposal.put("fieldKey", "price"); proposal.put("currentValue", "10,000원");
        proposal.put("proposedValue", "11,000원");
        proposal.put("title", "가격 조정"); proposal.put("beforeText", "10,000원");
        proposal.put("afterText", "11,000원"); proposal.put("rationale", "시장 근거 반영");
        proposal.put("source", "MARKET"); proposal.putArray("evidenceIds").add("E-1");
        result.putArray("driftRejections");
        return result;
    }

    private ExecutionResponse response(JsonNode result) {
        return new ExecutionResponse("internal-ai-execution-v1",
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(), "1.0", "task-1", "attempt-1",
            "request-1", HASH, "1.0", result, null, null, null);
    }
}
