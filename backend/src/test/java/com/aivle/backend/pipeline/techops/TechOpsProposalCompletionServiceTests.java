package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.techops.application.TechOpsProposalCompletionService;
import com.aivle.backend.pipeline.techops.application.TechOpsProposalCompletionService.Outcome;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TechOpsProposalCompletionServiceTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void initialBatchCommitsAllThreeMissingProposals() {
        Harness h = Harness.initial();

        Outcome outcome = h.service.complete(h.claim, h.context, h.response());

        assertThat(outcome).isEqualTo(Outcome.SUCCEEDED);
        JsonNode decisions = h.mapper.readTree(h.preparation.getProposalDecisionsJson());
        assertThat(decisions.path("deliveryOrProductionMethod").path("proposalValue").path("method").asText())
            .isEqualTo("온라인 직접 제공");
        assertThat(decisions.path("expectedMonthlyThroughputOrSales").path("proposalValue").path("amount").asInt())
            .isEqualTo(1000);
        assertThat(decisions.path("technicalSupplyOperationalConstraints").path("proposalValue")).hasSize(1);
        assertThat(h.preparation.getProposalGenerationStatus()).isEqualTo("SUCCEEDED");
        verify(h.taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token-1"),
            anyString(), eq(HASH), eq("1.0"));
    }

    @Test
    void alternativeSuccessCreatesNextProposalVersionAndClearsPending() {
        Harness h = Harness.alternative();

        Outcome outcome = h.service.complete(h.claim, h.context, h.response());

        assertThat(outcome).isEqualTo(Outcome.SUCCEEDED);
        JsonNode field = h.mapper.readTree(h.preparation.getProposalDecisionsJson())
            .path("deliveryOrProductionMethod");
        assertThat(field.path("proposalVersion").asInt()).isEqualTo(2);
        assertThat(field.path("proposalValue").path("method").asText()).isEqualTo("파트너 공동 제공");
        assertThat(field.path("finalValue").isNull()).isTrue();
        assertThat(field.path("pendingAlternativeTaskRunId").isNull()).isTrue();
    }

    @Test
    void directUserEditWinsOverLateAlternativeResult() {
        Harness h = Harness.alternative();
        ObjectNode decisions = (ObjectNode) h.mapper.readTree(h.preparation.getProposalDecisionsJson());
        ObjectNode field = (ObjectNode) decisions.path("deliveryOrProductionMethod");
        field.set("finalValue", h.mapper.readTree("{\"method\":\"사용자 직접 운영\"}"));
        field.put("source", "USER_INPUT"); field.put("decision", "USER_EDITED_ACCEPTED");
        field.putNull("pendingAlternativeTaskRunId"); field.put("alternativeRequested", false);
        h.preparation.updateProposalDecisions(h.mapper.writeValueAsString(decisions), 7L);

        Outcome outcome = h.service.complete(h.claim, h.context, h.response());

        assertThat(outcome).isEqualTo(Outcome.STALE);
        JsonNode retained = h.mapper.readTree(h.preparation.getProposalDecisionsJson())
            .path("deliveryOrProductionMethod");
        assertThat(retained.path("finalValue").path("method").asText()).isEqualTo("사용자 직접 운영");
        verify(h.taskRuns).fail("task-1", "attempt-1", "token-1",
            "EXECUTION_FAILED", "STALE_ACTION_RESULT", false);
    }

    @Test
    void finalizedSnapshotBlocksLateWorkerCommit() {
        Harness h = Harness.alternative();
        when(h.snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull("prep-1", 41L))
            .thenReturn(Optional.of(mock(TechOpsInputSnapshot.class)));

        Outcome outcome = h.service.complete(h.claim, h.context, h.response());

        assertThat(outcome).isEqualTo(Outcome.STALE);
        assertThat(h.mapper.readTree(h.preparation.getProposalDecisionsJson())
            .path("deliveryOrProductionMethod").path("proposalVersion").asInt()).isEqualTo(1);
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final TechOpsInputPreparationRepository preparations = mock(TechOpsInputPreparationRepository.class);
        final TechOpsInputSnapshotRepository snapshots = mock(TechOpsInputSnapshotRepository.class);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final TechOpsProposalCompletionService service = new TechOpsProposalCompletionService(
            preparations, snapshots, taskRuns, mapper);
        final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
        final TechOpsInputPreparation preparation;
        final TaskRunWorkerContext context;
        final boolean alternative;

        static Harness initial() { return new Harness(false); }
        static Harness alternative() { return new Harness(true); }

        Harness(boolean alternative) {
            this.alternative = alternative;
            ObjectNode decisions = emptyDecisions(mapper);
            if (alternative) {
                ObjectNode field = (ObjectNode) decisions.path("deliveryOrProductionMethod");
                field.set("proposalValue", mapper.readTree("{\"method\":\"온라인 직접 제공\"}"));
            }
            preparation = TechOpsInputPreparation.create("prep-1", 41L, "seed-1", HASH,
                "{}", mapper.writeValueAsString(decisions), 7L);
            JsonNode input;
            if (alternative) {
                field(decisions).put("alternativeRequested", true);
                field(decisions).put("pendingAlternativeTaskRunId", "task-1");
                preparation.queueAlternativeTask("task-1", mapper.writeValueAsString(decisions), 7L);
                input = mapper.readTree("""
                    {"mode":"ALTERNATIVE","preparationId":"prep-1","fieldKey":"deliveryOrProductionMethod",
                     "currentProposalVersion":1,"proposalVersion":2,"sourceMarketSeedSnapshotId":"seed-1",
                     "sourceSnapshotHash":"%s","expectedPreparationRevision":2}
                    """.formatted(HASH));
            } else {
                preparation.queueInitialProposalTask("task-1");
                input = mapper.readTree("""
                    {"mode":"INITIAL","preparationId":"prep-1","proposalVersion":1,
                     "sourceMarketSeedSnapshotId":"seed-1","sourceSnapshotHash":"%s",
                     "expectedPreparationRevision":1}
                    """.formatted(HASH));
            }
            context = new TaskRunWorkerContext("task-1", 41L, 7L, TaskType.TECH_OPS_PROPOSAL,
                "TECH_OPS_PREPARATION", "prep-1", mapper.writeValueAsString(input), HASH,
                "command-1", "request-1", "1.0", "1.0", "ko-KR", 1, 1);
            when(preparations.findLocked("prep-1", 41L)).thenReturn(Optional.of(preparation));
            when(snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull("prep-1", 41L))
                .thenReturn(Optional.empty());
        }

        ExecutionResponse response() {
            String delivery = alternative ? "파트너 공동 제공" : "온라인 직접 제공";
            JsonNode result = mapper.readTree("""
                {"deliveryOrProductionMethod":{"method":"%s","operatingModel":"직접","partnerModel":""},
                 "expectedMonthlyThroughputOrSales":{"amount":1000.0,"unit":"건"},
                 "technicalSupplyOperationalConstraints":["월별 공급 한도를 확인합니다."],
                 "assumptions":["사용자 확인 전 가설입니다."],"explanation":"운영 제안"}
                """.formatted(delivery));
            ExecutionResponse response = mock(ExecutionResponse.class);
            when(response.result()).thenReturn(result);
            when(response.canonicalInputHash()).thenReturn(HASH);
            when(response.resultSchemaVersion()).thenReturn("1.0");
            return response;
        }

        private static ObjectNode emptyDecisions(ObjectMapper mapper) {
            ObjectNode root = mapper.createObjectNode();
            for (String key : java.util.List.of("deliveryOrProductionMethod",
                    "expectedMonthlyThroughputOrSales", "technicalSupplyOperationalConstraints")) {
                ObjectNode field = root.putObject(key);
                field.putNull("proposalValue"); field.putNull("finalValue");
                field.put("source", "AI_HYPOTHESIS"); field.put("decision", "PROPOSED");
                field.put("proposalVersion", 1); field.put("alternativeRequested", false);
                field.putNull("pendingAlternativeTaskRunId");
            }
            return root;
        }

        private static ObjectNode field(ObjectNode decisions) {
            return (ObjectNode) decisions.path("deliveryOrProductionMethod");
        }
    }
}
