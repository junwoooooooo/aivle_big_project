package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NewPipelineFoundationMigrationTests {
    @Test
    void definesAdditiveProjectScopedPipelineFoundation() throws IOException {
        String sql;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
            "db/migration/V1__new_pipeline_baseline.sql"
        )) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
            "CREATE TABLE module_runs",
            "CREATE TABLE module_handoffs",
            "CREATE TABLE module_results",
            "CREATE TABLE market_analysis_seed_snapshots"
        );
        assertThat(sql).contains(
            "REFERENCES projects(id)",
            "REFERENCES users(id)",
            "REFERENCES stored_files(id)",
            "REFERENCES market_analysis_seed_snapshots(id, project_id)"
        );
        assertThat(sql).contains(
            "uk_module_handoff_idempotency",
            "uk_module_run_handoff",
            "uk_market_seed_snapshot_selection",
            "ck_market_seed_snapshot_hash"
        );
        assertThat(sql).doesNotContain(
            "CREATE TABLE planning_snapshots",
            "CREATE TABLE finalized_planning_snapshots",
            "CREATE TABLE planning_change_proposals",
            "CREATE TABLE selected_concept_snapshots",
            "DROP TABLE", "TRUNCATE TABLE", "DELETE FROM"
        );
    }
}
