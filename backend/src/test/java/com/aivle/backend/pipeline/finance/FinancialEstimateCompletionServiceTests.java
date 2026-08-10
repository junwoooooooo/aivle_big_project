package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.finance.application.FinancialEstimateCompletionService;
import com.aivle.backend.pipeline.finance.application.FinancialEstimateCompletionService.Outcome;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class FinancialEstimateCompletionServiceTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void workerSuccessStoresProposalWithoutChangingFinancialField() {
        Harness h = new Harness(1, null);

        Outcome outcome = h.service.complete(h.claim, h.context, h.response(12000000));

        assertThat(outcome).isEqualTo(Outcome.SUCCEEDED);
        JsonNode proposal = h.assistance();
        assertThat(proposal.path("proposalValue").path("amount").asInt()).isEqualTo(12000000);
        assertThat(proposal.path("proposalVersion").asInt()).isEqualTo(1);
        assertThat(proposal.path("estimateStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(h.fields().path("annualFixedRentAndManagementCost").path("value").isNull()).isTrue();
        verify(h.taskRuns).adopt(eq("task-1"), eq("attempt-1"), eq("token-1"),
            anyString(), eq(HASH), eq("1.0"));
    }

    @Test
    void alternativeCreatesVersionTwoAndMustDiffer() {
        Harness h = new Harness(2, hMoney(12000000));

        h.service.complete(h.claim, h.context, h.response(14000000));

        assertThat(h.assistance().path("proposalVersion").asInt()).isEqualTo(2);
        assertThat(h.assistance().path("proposalValue").path("amount").asInt()).isEqualTo(14000000);
    }

    @Test
    void directEditWinsOverLateEstimate() {
        Harness h = new Harness(1, null);
        ObjectNode fields = (ObjectNode) h.fields();
        fields.withObject("annualFixedRentAndManagementCost").set("value", hMoney(9000000));
        h.preparation.updateFinancialFields(h.mapper.writeValueAsString(fields), 7L);

        Outcome outcome = h.service.complete(h.claim, h.context, h.response(12000000));

        assertThat(outcome).isEqualTo(Outcome.STALE);
        assertThat(h.fields().path("annualFixedRentAndManagementCost").path("value").path("amount").asInt())
            .isEqualTo(9000000);
        assertThat(h.assistance().path("proposalValue").isNull()).isTrue();
    }

    @Test
    void failurePreservesExistingProposalAndExposesSafeStatus() {
        Harness h = new Harness(2, hMoney(12000000));

        h.service.fail(h.claim, h.context, "RATE_LIMITED", "provider detail", false);

        assertThat(h.assistance().path("proposalValue").path("amount").asInt()).isEqualTo(12000000);
        assertThat(h.assistance().path("estimateStatus").asText()).isEqualTo("FAILED");
        assertThat(h.assistance().path("safeError").asText()).isEqualTo("RATE_LIMITED");
    }

    private static JsonNode hMoney(int amount) {
        return new ObjectMapper().readTree("{\"amount\":" + amount + ",\"currency\":\"KRW\"}");
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final FinancialInputPreparationRepository preparations = mock(FinancialInputPreparationRepository.class);
        final FinancialInputSnapshotRepository snapshots = mock(FinancialInputSnapshotRepository.class);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final FinancialEstimateCompletionService service = new FinancialEstimateCompletionService(
            preparations, snapshots, taskRuns, mapper);
        final TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
        final FinancialInputPreparation preparation;
        final TaskRunWorkerContext context;

        Harness(int version, JsonNode oldProposal) {
            ObjectNode fields = mapper.createObjectNode();
            ObjectNode field = fields.putObject("annualFixedRentAndManagementCost");
            field.putNull("value"); field.put("readOnly", false); field.put("source", "USER_INPUT"); field.put("decision", "OPEN");
            ObjectNode assistance = mapper.createObjectNode();
            ObjectNode proposal = assistance.putObject("annualFixedRentAndManagementCost");
            if (oldProposal == null) proposal.putNull("proposalValue"); else proposal.set("proposalValue", oldProposal.deepCopy());
            proposal.put("proposalVersion", Math.max(0, version - 1));
            proposal.put("decision", "PROPOSED"); proposal.put("estimateStatus", "RUNNING");
            proposal.put("activeTaskRunId", "task-1");
            preparation = FinancialInputPreparation.create("prep-1", 41L, "tech-1", "seed-1", HASH,
                mapper.writeValueAsString(fields), "{}", mapper.writeValueAsString(assistance), 7L);
            String rejected = oldProposal == null ? "" : mapper.writeValueAsString(oldProposal);
            JsonNode input = mapper.readTree("""
                {"preparationId":"prep-1","fieldKey":"annualFixedRentAndManagementCost",
                 "proposalVersion":%d,"rejectedProposalJson":%s,"sourceTechOpsSnapshotId":"tech-1",
                 "sourceSnapshotHash":"%s","expectedPreparationRevision":1}
                """.formatted(version, mapper.writeValueAsString(rejected), HASH));
            context = new TaskRunWorkerContext("task-1", 41L, 7L, TaskType.FINANCE_ESTIMATE,
                "FINANCIAL_PREPARATION", "prep-1", mapper.writeValueAsString(input), HASH,
                "command-1", "request-1", "1.0", "1.0", "ko-KR", 1, 1);
            when(preparations.findLocked("prep-1", 41L)).thenReturn(Optional.of(preparation));
            when(snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull("prep-1", 41L))
                .thenReturn(Optional.empty());
        }

        ExecutionResponse response(int amount) {
            JsonNode result = mapper.readTree("""
                {"fieldKey":"annualFixedRentAndManagementCost",
                 "proposedValue":{"amount":%d,"currency":"KRW"},
                 "assumptions":["월 임차료 기준"],"explanation":"운영 규모 기준 추천",
                 "confidence":"MEDIUM","source":"AI_ESTIMATE"}
                """.formatted(amount));
            ExecutionResponse response = mock(ExecutionResponse.class);
            when(response.result()).thenReturn(result);
            when(response.canonicalInputHash()).thenReturn(HASH);
            when(response.resultSchemaVersion()).thenReturn("1.0");
            return response;
        }

        JsonNode fields() { return mapper.readTree(preparation.getFinancialFieldsJson()); }
        JsonNode assistance() {
            return mapper.readTree(preparation.getAssistanceJson()).path("annualFixedRentAndManagementCost");
        }
    }
}
