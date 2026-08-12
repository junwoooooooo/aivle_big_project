package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.finance.application.*;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(initial.assistance().has("newCustomerCount")).isTrue();
    }

    @Test
    void preparationBindsMarketBmAndTechOpsWithDistinctProvenanceAndNoDemoFallback() {
        var source = TechOpsInputSnapshot.create("tech-1", 41L, "tech-prep-1", "seed-1", "2.0",
            "sha256:" + "a".repeat(64), "{\"requiredFacts\":{},\"requiredFactProvenance\":{}}",
            7L, Instant.EPOCH);
        var market = mapper.readTree("""
            {"market":{"tam":{"value":100,"unit":"억원"},"sam":{"value":20,"unit":"억원"},
             "growth":{"value":12,"unit":"%"},"price":{"base":9900,"currency":"KRW"}},
             "evidence":[{"sourceId":"ev-1"}],"scorecard":[{"state":"FILLED"}]}
            """);
        var bm = mapper.readTree("""
            {"bm":{"financialHandoff":{"revenueModel":"구독","pricingLogic":"월 구독"},
             "decision":"GO","caveats":["가격 검증 필요"]}}
            """);
        var initial = new FinancialPreparationFactory(mapper).create(source, market, bm, 101L, 201L);
        assertThat(initial.financialFields().path("unitPrice").path("source").asText())
            .isEqualTo("MARKET_ANALYSIS_ASSUMPTION");
        assertThat(initial.financialFields().path("unitPrice").path("provenance").asText())
            .isEqualTo("market.price.base");
        assertThat(initial.financialFields().path("revenueModel").path("value").asText())
            .isEqualTo("SUBSCRIPTION");
        assertThat(initial.financialFields().path("revenueModel").path("source").asText())
            .isEqualTo("BUSINESS_MODEL_ASSUMPTION");
        assertThat(initial.upstreamReferences().path("marketAnalysis").path("sourceVersionId").asLong()).isEqualTo(101L);
        assertThat(initial.upstreamReferences().path("businessModel").path("sourceVersionId").asLong()).isEqualTo(201L);
        assertThat(initial.upstreamReferences().path("techOpsSnapshot").path("sourceSnapshotId").asText()).isEqualTo("tech-1");
        assertThat(initial.upstreamReferences().toString()).doesNotContainIgnoringCase("demo", "sample");
    }

    @Test
    void marketBmPreparationUsesPrecedenceWithoutTechOpsAndNeverOverwritesUserDecision() {
        var market = mapper.readTree("""
            {"market":{"tam":{"value":100},"sam":{"value":20},"growth":{"value":12},
             "price":{"base":10000,"currency":"KRW"}}}
            """);
        var bm = mapper.readTree("""
            {"bm":{"financialHandoff":{"revenueModel":"월 구독","priceBase":12000}}}
            """);
        var concept = mapper.readTree("""
            {"revenueModel":{"value":"월 구독"},"price":{"value":"월 9,000원"}}
            """);
        var factory = new FinancialPreparationFactory(mapper);
        var initial = factory.createFromMarketAndBusinessModel(market, bm, concept, 101L, 201L);

        assertThat(initial.financialFields().path("revenueModel").path("source").asText())
            .isEqualTo("BUSINESS_MODEL_ASSUMPTION");
        assertThat(initial.financialFields().path("monthlySubscriptionPrice").path("value").path("amount").asInt())
            .isEqualTo(12000);
        assertThat(initial.upstreamReferences().has("techOpsSnapshot")).isFalse();
        assertThat(initial.financialFields().path("annualFixedLaborCost").path("decision").asText()).isEqualTo("OPEN");

        ObjectNode fields = initial.financialFields();
        fields.withObject("monthlySubscriptionPrice").withObject("value").put("amount", 15000);
        fields.withObject("monthlySubscriptionPrice").put("source", "USER_INPUT").put("decision", "LOCKED");
        factory.applyBusinessModelDefaults(fields, bm);
        assertThat(fields.path("monthlySubscriptionPrice").path("value").path("amount").asInt()).isEqualTo(15000);
        assertThat(fields.path("monthlySubscriptionPrice").path("source").asText()).isEqualTo("USER_INPUT");
    }

    @Test
    void readinessRequiresOnlyMandatoryFieldsAndNotConditionalCosts() {
        var fields = mapper.createObjectNode();
        FinancialPreparationFactory.REQUIRED_KEYS.forEach(key -> fields.putObject(key).put("value", key).put("decision", "LOCKED"));
        fields.withObject("revenueModel").put("value", "ONE_TIME");
        fields.withObject("unitPrice").set("value", mapper.readTree("{\"amount\":1000,\"currency\":\"KRW\"}"));
        fields.withObject("unitPrice").put("decision", "LOCKED");
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

    @Test
    void reopenKeepsFinalizedSnapshotAsHistoryAndV19AllowsOneReplacementActiveSnapshot() throws Exception {
        String hash = "sha256:" + "a".repeat(64);
        var historical = FinancialInputSnapshot.create("snapshot-old", 41L, "prep-1", "tech-1", "seed-1",
            101L, 201L, "2.0", hash, "{}", 7L, Instant.EPOCH);
        historical.softDelete();
        var replacement = FinancialInputSnapshot.create("snapshot-new", 41L, "prep-1", "tech-1", "seed-1",
            101L, 201L, "2.0", hash, "{}", 7L, Instant.EPOCH.plusSeconds(1));
        assertThat(historical.isDeleted()).isTrue();
        assertThat(historical.getId()).isEqualTo("snapshot-old");
        assertThat(replacement.isDeleted()).isFalse();
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V19__financial_snapshot_active_preparation_unique.sql"));
        assertThat(migration).contains("WHERE deleted_at IS NULL", "uk_financial_snapshot_active_preparation");
    }

    @Test
    void v20MakesTechOpsOptionalAndUsesMarketBmActiveAuthority() throws Exception {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V20__finance_market_bm_authority.sql"));
        assertThat(migration).contains("source_tech_ops_snapshot_id DROP NOT NULL",
            "source_market_research_version_id", "source_business_model_version_id");
        assertThat(migration).doesNotContain("ON financial_input_preparations(project_id, source_tech_ops_snapshot_id");
    }
}
