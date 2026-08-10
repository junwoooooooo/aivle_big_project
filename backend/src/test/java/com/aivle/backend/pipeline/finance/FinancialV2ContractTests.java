package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.finance.domain.FinancialInputSnapshot;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.module.PipelineModuleType;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinancialV2ContractTests {
    @Test
    void snapshotIsImmutableAndFinanceIsIndependentModule() {
        assertThat(Arrays.stream(FinancialInputSnapshot.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && (method.getName().startsWith("set")
                || method.getName().startsWith("update") || method.getName().startsWith("replace"))).toList()).isEmpty();
        var snapshot = FinancialInputSnapshot.create("finance-1", 41L, "prep-1", "tech-1", "seed-1", "2.0",
            "sha256:" + "a".repeat(64), "{}", 7L, Instant.EPOCH);
        assertThat(snapshot.getFinalizedAt()).isEqualTo(Instant.EPOCH);
        assertThat(ModuleType.values()).contains(ModuleType.FINANCIAL_ANALYSIS);
        assertThat(PipelineModuleType.values()).contains(PipelineModuleType.FINANCE);
    }

    @Test
    void baselineAndSchemaExposePreparationSnapshotAndHandoffBoundary() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V1__new_pipeline_baseline.sql"))
            .toLowerCase(Locale.ROOT);
        assertThat(sql).contains("create table financial_input_preparations", "create table financial_input_snapshots", "'financial_analysis'");
        var schema = new ObjectMapper().readTree(Files.readString(Path.of("..", "docs", "rebuild", "contracts", "financial-input-snapshot-v1.schema.json")));
        assertThat(schema.path("properties").path("contract").path("const").asText()).isEqualTo("financial-input-snapshot-v1");
        assertThat(schema.path("required").toString()).contains("sourceTechOpsSnapshotId", "values", "valueProvenance", "calculatedCac", "hash");
    }
}
