package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.finance.application.*;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class FinancialPreparationContractsTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preparationInheritsExactTechOpsValuesAndLeavesOnlyMissingDetailsOpen() {
        var source = TechOpsInputSnapshot.create("tech-1", 41L, "tech-prep-1", "seed-1", "2.0",
            "sha256:" + "a".repeat(64), """
            {"requiredFacts":{
              "fixedOperatingCost":{"amount":100,"currency":"KRW","period":"MONTHLY","breakdown":{"annualFixedLaborCost":{"amount":900,"currency":"KRW"}}},
              "initialInvestment":{"amount":300,"currency":"KRW"},
              "threeYearTargets":{"metric":"customerCount","unit":"명","years":[{"year":1,"value":100},{"year":2,"value":200},{"year":3,"value":300}]}
            },"requiredFactProvenance":{
              "fixedOperatingCost":{"source":"USER_INPUT","decision":"LOCKED"},
              "initialInvestment":{"source":"USER_INPUT","decision":"LOCKED"},
              "threeYearTargets":{"source":"USER_INPUT","decision":"LOCKED"}
            }}
            """, 7L, Instant.EPOCH);

        var initial = new FinancialPreparationFactory(mapper).create(source);

        assertThat(initial.financialFields().path("annualFixedLaborCost").path("readOnly").asBoolean()).isTrue();
        assertThat(initial.financialFields().path("annualFixedLaborCost").path("sourceSnapshotId").asText()).isEqualTo("tech-1");
        assertThat(initial.financialFields().path("annualFixedRentAndManagementCost").path("decision").asText()).isEqualTo("OPEN");
        assertThat(initial.financialFields().path("threeYearTargets").path("readOnly").asBoolean()).isTrue();
        assertThat(initial.upstreamReferences().path("fixedOperatingCost").path("annualEquivalent").path("amount").decimalValue())
            .isEqualByComparingTo("1200");
        assertThat(initial.assistance().path("cac").path("proposalValue").isNull()).isTrue();
        assertThat(initial.assistance().path("cac").path("decision").asText()).isEqualTo("PROPOSED");
        assertThat(initial.assistance().path("threeYearTargets").path("estimateStatus").asText()).isEqualTo("NONE");
        assertThat(initial.financialFields().path("threeYearTargets").path("readOnly").asBoolean()).isTrue();
        assertThat(initial.assistance().has("newCustomerCount")).isFalse();
    }

    @Test
    void readinessRequiresOnlyMandatoryFieldsAndNotConditionalCosts() {
        var fields = mapper.createObjectNode();
        FinancialPreparationFactory.REQUIRED_KEYS.forEach(key -> fields.putObject(key).put("value", key).put("decision", "LOCKED"));
        FinancialPreparationFactory.CONDITIONAL_COST_KEYS.forEach(key -> fields.putObject(key).putNull("value"));
        assertThat(new FinancialReadiness().missing(fields)).isEmpty();
        fields.withObject("annualFixedInfrastructureCost").putNull("value");
        assertThat(new FinancialReadiness().missing(fields)).containsExactly("annualFixedInfrastructureCost");
    }

    @Test
    void cacIsCalculatedBySystemAndSnapshotHashIsStable() {
        var fields = mapper.createObjectNode();
        FinancialPreparationFactory.ALL_KEYS.forEach(key -> fields.putObject(key).putNull("value"));
        fields.withObject("totalMarketingCost").set("value", mapper.readTree("{\"amount\":1000,\"currency\":\"KRW\"}"));
        fields.withObject("totalSalesCost").set("value", mapper.readTree("{\"amount\":500,\"currency\":\"KRW\"}"));
        fields.withObject("newCustomerCount").put("value", 30);
        var calculator = new FinancialCalculator(mapper);
        assertThat(calculator.calculateCac(fields).path("amount").decimalValue()).isEqualByComparingTo("50.00");

        var preparation = FinancialInputPreparation.create("finance-prep-1", 41L, "tech-1", "seed-1",
            "sha256:" + "a".repeat(64), mapper.writeValueAsString(fields), "{}", "{}", 7L);
        var factory = new FinancialInputSnapshotFactory(mapper, new SnapshotHasher(mapper), calculator);
        var first = factory.create("finance-1", Instant.EPOCH, preparation);
        var second = factory.create("finance-1", Instant.EPOCH, preparation);
        assertThat(first.hash()).isEqualTo(second.hash()).matches("sha256:[0-9a-f]{64}");
        assertThat(first.body().path("calculatedCac").path("source").asText()).isEqualTo("SYSTEM_CALCULATION");
        assertThat(first.body().path("sourceTechOpsSnapshotId").asText()).isEqualTo("tech-1");
    }

    @Test
    void snapshotExcludesUnacceptedProposedEstimate() {
        ObjectNode fields = mapper.createObjectNode();
        FinancialPreparationFactory.ALL_KEYS.forEach(key -> fields.putObject(key).putNull("value"));
        ObjectNode assistance = mapper.createObjectNode();
        assistance.putObject("totalMarketingCost")
            .set("proposalValue", mapper.readTree("{\"amount\":1000,\"currency\":\"KRW\"}"));
        assistance.withObject("totalMarketingCost").put("decision", "PROPOSED");
        assistance.withObject("totalMarketingCost").put("estimateStatus", "SUCCEEDED");
        var preparation = FinancialInputPreparation.create("finance-prep-2", 41L, "tech-1", "seed-1",
            "sha256:" + "a".repeat(64), mapper.writeValueAsString(fields), "{}",
            mapper.writeValueAsString(assistance), 7L);

        var snapshot = new FinancialInputSnapshotFactory(mapper, new SnapshotHasher(mapper),
            new FinancialCalculator(mapper)).create("finance-2", Instant.EPOCH, preparation);

        assertThat(snapshot.body().path("assistance").path("totalMarketingCost").has("proposalValue")).isFalse();
        assertThat(snapshot.body().path("assistance").path("totalMarketingCost").has("estimateStatus")).isFalse();
    }
}
