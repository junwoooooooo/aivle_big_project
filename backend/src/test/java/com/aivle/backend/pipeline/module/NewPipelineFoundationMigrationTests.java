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
            "db/migration/V6__new_pipeline_foundation.sql"
        )) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
            "CREATE TABLE pipeline_module_runs",
            "CREATE TABLE module_handoffs",
            "CREATE TABLE module_results",
            "CREATE TABLE planning_snapshots"
        );
        assertThat(sql).contains(
            "REFERENCES projects(id)",
            "REFERENCES users(id)",
            "REFERENCES stored_files(id)",
            "REFERENCES task_runs(id, project_id)"
        );
        assertThat(sql).contains(
            "uk_pipeline_module_run_sequence",
            "uk_module_handoff_idempotency",
            "uk_module_result_sequence",
            "uk_planning_snapshot_sequence",
            "uk_planning_snapshot_hash"
        );
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE TABLE", "DELETE FROM");
    }
}
