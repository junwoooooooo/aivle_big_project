package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.marketing.application.MarketingContentService;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketingSourceV2ContractTests {
    @Test
    void sourceEntityIsImmutableAndCarriesSnapshotIdentity() {
        var mutators = Arrays.stream(MarketingSourceSnapshot.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getName().startsWith("set") || method.getName().startsWith("update")
                || method.getName().startsWith("replace") || method.getName().startsWith("regenerate"))
            .toList();
        assertThat(mutators).isEmpty();
        var source = MarketingSourceSnapshot.create("source-1", 1L, "market-seed-1", 2L, "concept-1", "2.0",
            "sha256:" + "a".repeat(64), "{}", 7L, Instant.EPOCH);
        assertThat(source.getSourceMarketSeedSnapshotId()).isEqualTo("market-seed-1");
        assertThat(source.getFinalizedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void marketingServiceHasNoPlanningOrMarketResultDependency() {
        var dependencies = Arrays.stream(MarketingContentService.class.getDeclaredFields())
            .map(field -> field.getType().getSimpleName()).toList();
        assertThat(dependencies).doesNotContain("FinalizedPlanningSnapshotRepository", "PlanningSnapshotRepository",
            "MarketAnalysisResultRepository", "MarketResultIntakeService");
    }

    @Test
    void sqlUsesMarketingSourceForeignKeyInsteadOfPlanning() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V1__new_pipeline_baseline.sql"))
            .toLowerCase(Locale.ROOT);
        assertThat(sql).contains("create table marketing_source_snapshots",
            "uk_marketing_source_market_seed unique (source_market_seed_snapshot_id)",
            "marketing_source_snapshot_id varchar(64) not null",
            "references marketing_source_snapshots(id, project_id)");
        String marketingTable = sql.substring(sql.indexOf("create table pipeline_marketing_contents"),
            sql.indexOf("create table pipeline_marketing_content_revisions"));
        assertThat(marketingTable).doesNotContain("planning_snapshot_id", "finalized_planning_snapshots");
    }

    @Test
    void requestAndSourceSchemasUseTheNewBoundary() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var request = mapper.readTree(Files.readString(Path.of("..", "docs", "rebuild", "contracts", "marketing-content-request-v1.schema.json")));
        var source = mapper.readTree(Files.readString(Path.of("..", "docs", "rebuild", "contracts", "marketing-source-snapshot-v1.schema.json")));
        assertThat(request.path("required").toString()).contains("marketingSourceSnapshotId").doesNotContain("planningSnapshotId");
        assertThat(source.path("required").toString()).contains("allowedClaims", "prohibitedClaims",
            "requiredDisclosures", "requiredControls", "communicationRequiredControls", "hash", "createdAt");
    }
}
