package com.aivle.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
class PostgreSqlBaselineMigrationTests extends PostgreSqlIntegrationTestSupport {
    @Test
    void appliesAndValidatesAllNewPipelineMigrationsOnAnEmptySchema() throws Exception {
        String schema = "baseline_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
        flyway.validate();

        var appliedVersions = Arrays.stream(flyway.info().applied())
            .filter(info -> info.getVersion() != null)
            .map(info -> info.getVersion().getVersion())
            .toList();

        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");

        try (Connection connection = DriverManager.getConnection(
                 POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertTables(connection, schema,
                "users", "refresh_tokens", "projects", "stored_files", "audit_events",
                "service_settings", "admin_action_tokens", "task_runs", "task_attempts",
                "task_results", "job_events", "idea_briefs", "idea_brief_fields",
                "idea_questions", "idea_answers", "idea_brief_attachments",
                "legal_context_packs", "legal_evidence", "concept_factory_runs", "concept_slots",
                "concept_attempts", "concepts", "concept_legal_assessments",
                "concept_legal_evidence_links", "concept_rejection_summaries", "concept_selections",
                "market_analysis_seed_snapshots", "module_handoffs", "module_runs", "module_results",
                "tech_ops_input_preparations", "tech_ops_evidence_references",
                "tech_ops_input_snapshots", "marketing_source_snapshots", "pipeline_marketing_contents",
                "pipeline_marketing_content_revisions", "pipeline_marketing_assets");

            assertTablesAbsent(connection, schema,
                "project_documents", "document_versions", "structured_plans",
                "structured_plan_sections", "analysis_jobs", "financial_analyses",
                "persona_studies", "marketing_workspaces", "final_reports",
                "selected_concept_snapshots", "planning_change_proposals", "planning_change_decisions",
                "planning_snapshots", "finalized_planning_snapshots");
        }
    }

    @Test
    void upgradesAnExistingV1ThroughV7SchemaWithContractHardeningMigration() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV7 = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration")
            .target("7").load();
        assertThat(throughV7.migrate().migrationsExecuted).isEqualTo(7);

        Flyway latest = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isOne();
        latest.validate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(columnCount(connection, schema, "concept_slots", "replacement_rounds")).isOne();
            assertThat(columnCount(connection, schema, "concept_rejection_summaries", "attempt_id")).isOne();
        }
    }

    private void assertTables(Connection connection, String schema, String... tables) throws Exception {
        for (String table : tables) {
            assertThat(tableCount(connection, schema, table)).as("table %s", table).isEqualTo(1);
        }
    }

    private void assertTablesAbsent(Connection connection, String schema, String... tables) throws Exception {
        for (String table : tables) {
            assertThat(tableCount(connection, schema, table)).as("legacy table %s", table).isZero();
        }
    }

    private int tableCount(Connection connection, String schema, String table) throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*) from information_schema.tables
            where table_schema = ? and table_name = ?
            """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int columnCount(Connection connection, String schema, String table, String column) throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*) from information_schema.columns
            where table_schema = ? and table_name = ? and column_name = ?
            """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
