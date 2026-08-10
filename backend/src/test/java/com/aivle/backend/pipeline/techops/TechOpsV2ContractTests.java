package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.module.PipelineModuleType;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TechOpsV2ContractTests {
    @Test
    void snapshotIsImmutableAndTechOpsIsIndependentModule() {
        assertThat(Arrays.stream(TechOpsInputSnapshot.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && (method.getName().startsWith("set")
                || method.getName().startsWith("update") || method.getName().startsWith("replace"))).toList()).isEmpty();
        var snapshot=TechOpsInputSnapshot.create("snapshot-1",1L,"prep-1","seed-1","2.0",
            "sha256:"+"a".repeat(64),"{}",7L,Instant.EPOCH);
        assertThat(snapshot.getFinalizedAt()).isEqualTo(Instant.EPOCH);
        assertThat(ModuleType.values()).contains(ModuleType.TECH_OPS);
        assertThat(PipelineModuleType.values()).contains(PipelineModuleType.TECH_OPS);
    }

    @Test
    void baselineAndSchemaExposePreparationEvidenceSnapshotAndHandoffBoundary() throws Exception {
        String sql=Files.readString(Path.of("src","main","resources","db","migration","V1__new_pipeline_baseline.sql")).toLowerCase(Locale.ROOT);
        assertThat(sql).contains("create table tech_ops_input_preparations", "create table tech_ops_evidence_references",
            "create table tech_ops_input_snapshots", "'tech_ops'");
        String artifactSql = Files.readString(Path.of("src", "main", "resources", "db", "migration",
            "V5__v2_10f_project_evidence_artifacts.sql")).toLowerCase(Locale.ROOT);
        assertThat(artifactSql).contains("create table project_evidence_artifacts",
            "add column artifact_id", "fk_tech_ops_evidence_artifact", "alter column artifact_ref drop not null");
        var schema=new ObjectMapper().readTree(Files.readString(Path.of("..","docs","rebuild","contracts","tech-ops-input-snapshot-v1.schema.json")));
        assertThat(schema.path("properties").path("contract").path("const").asText()).isEqualTo("tech-ops-input-snapshot-v1");
        assertThat(schema.path("required").toString()).contains("snapshotId","hash","createdAt","requiredFacts",
            "requiredDecisions","evidenceReferences");
        assertThat(schema.path("$defs").path("evidence").path("required").toString())
            .contains("artifactId", "originalFilename", "mediaType", "sizeBytes", "sha256")
            .doesNotContain("artifactRef", "storageKey");
    }
}
