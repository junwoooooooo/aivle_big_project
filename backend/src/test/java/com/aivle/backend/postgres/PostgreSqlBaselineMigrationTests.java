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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(23);
        flyway.validate();

        var appliedVersions = Arrays.stream(flyway.info().applied())
            .filter(info -> info.getVersion() != null)
            .map(info -> info.getVersion().getVersion())
            .toList();

        assertThat(appliedVersions).containsExactly(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
            "18", "19", "20", "21", "22", "23", "24");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("24");

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
                "financial_input_preparations", "financial_input_snapshots",
                "pipeline_marketing_content_revisions", "pipeline_marketing_assets",
                "concept_portfolio_runs", "concept_portfolio_concepts", "concept_portfolio_continuations",
                "concept_input_requests", "concept_input_responses", "concept_portfolio_selections",
                "concept_portfolio_hypothesis_decisions", "concept_portfolio_delta_legal_reviews",
                "concept_legal_regulatory_reports", "market_research_runs", "market_research_versions",
                "twin_survey_runs", "twin_survey_versions", "bm_plan_preparations",
                "tech_ops_advisory_reports", "research_competitor_seeds",
                "market_research_ledger_artifacts");

            assertThat(constraintCount(connection, schema, "research_competitor_seeds",
                "fk_research_competitor_seed_project")).isOne();
            assertThat(constraintCount(connection, schema, "research_competitor_seeds",
                "fk_research_competitor_seed_user")).isOne();
            assertThat(constraintCount(connection, schema, "research_competitor_seeds",
                "ck_research_competitor_seed_order")).isOne();
            assertThat(indexCount(connection, schema, "research_competitor_seeds",
                "uk_research_competitor_seed_name")).isOne();
            assertThat(columnCount(connection, schema, "research_competitor_seeds", "version")).isOne();
            assertThat(columnCount(connection, schema, "research_competitor_seeds", "display_order")).isOne();
            assertThat(constraintCount(connection, schema, "market_research_ledger_artifacts",
                "fk_market_ledger_project")).isOne();
            assertThat(constraintCount(connection, schema, "market_research_ledger_artifacts",
                "fk_market_ledger_task")).isOne();
            assertThat(constraintCount(connection, schema, "market_research_ledger_artifacts",
                "fk_market_ledger_attempt")).isOne();
            assertThat(constraintCount(connection, schema, "market_research_ledger_artifacts",
                "uk_market_ledger_version")).isOne();
            assertThat(indexCount(connection, schema, "market_research_ledger_artifacts",
                "idx_market_ledger_project_source")).isOne();

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
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(16);
        latest.validate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(columnCount(connection, schema, "concept_slots", "replacement_rounds")).isOne();
            assertThat(columnCount(connection, schema, "concept_rejection_summaries", "attempt_id")).isOne();
        }
    }

    @Test
    void upgradesAnExistingV8SchemaWithRuntimeBudgetConstraints() throws Exception {
        String schema = "upgrade_v8_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV8 = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration")
            .target("8").load();
        assertThat(throughV8.migrate().migrationsExecuted).isEqualTo(8);

        Flyway latest = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(14);
        latest.validate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("SET session_replication_role = replica");
            statement.execute("""
                INSERT INTO concept_factory_runs (
                    id, project_id, source_idea_brief_snapshot_id, source_snapshot_hash, status,
                    replacement_rounds, inspected_candidate_count, provider_transient_retry_count,
                    created_by_user_id, created_at, updated_at, version
                ) VALUES ('budget-run', 1, 'brief-1',
                    'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    'VALIDATING', 0, 16, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
            statement.execute("UPDATE concept_factory_runs SET inspected_candidate_count = 20, provider_transient_retry_count = 3 WHERE id = 'budget-run'");
            assertThatThrownBy(() -> statement.execute(
                "UPDATE concept_factory_runs SET inspected_candidate_count = -1 WHERE id = 'budget-run'"))
                .hasMessageContaining("ck_concept_run_inspected");
            assertThatThrownBy(() -> statement.execute(
                "UPDATE concept_factory_runs SET provider_transient_retry_count = -1 WHERE id = 'budget-run'"))
                .hasMessageContaining("ck_concept_run_provider_retry");
            statement.execute("SET session_replication_role = origin");
        }
    }

    @Test
    void upgradesTheSessionOneV13SchemaThroughAllTransplantedLineageMigrations() throws Exception {
        String schema = "upgrade_v13_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV13 = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration")
            .target("13").load();
        assertThat(throughV13.migrate().migrationsExecuted).isEqualTo(13);

        Flyway latest = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(9);
        latest.validate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertTables(connection, schema, "market_research_runs", "market_research_versions",
                "twin_survey_runs", "twin_survey_versions", "bm_plan_preparations");
            assertThat(constraintCount(connection, schema, "market_research_runs", "fk_market_research_run_seed")).isOne();
            assertThat(constraintCount(connection, schema, "twin_survey_runs", "fk_twin_survey_run_selection")).isOne();
            assertThat(constraintCount(connection, schema, "financial_input_preparations", "fk_financial_preparation_bm_version")).isOne();
            assertThat(indexCount(connection, schema, "financial_input_snapshots", "uk_financial_snapshot_active_preparation")).isOne();
        }
    }

    @Test
    void v20StopsWithoutDeletingRowsWhenMarketBmAuthorityHasDuplicates() throws Exception {
        String schema = "v20_duplicate_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV19 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").target("19").load();
        throughV19.migrate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("SET session_replication_role = replica");
            statement.execute("""
                INSERT INTO financial_input_preparations (
                    id, project_id, source_tech_ops_snapshot_id, source_market_seed_snapshot_id,
                    source_market_research_version_id, source_business_model_version_id,
                    source_snapshot_hash, financial_fields_json, upstream_references_json, assistance_json,
                    revision, updated_by_user_id, created_at, updated_at, version
                ) VALUES
                    ('duplicate-a', 41, 'tech-a', 'seed-a', 101, 201,
                     'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '{}', '{}', '{}',
                     1, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
                    ('duplicate-b', 41, 'tech-b', 'seed-b', 101, 201,
                     'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', '{}', '{}', '{}',
                     1, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
            statement.execute("SET session_replication_role = origin");
        }
        Flyway latest = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();

        assertThatThrownBy(latest::migrate).hasMessageContaining("V20");
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet result = statement.executeQuery(
                    "SELECT count(*) FROM financial_input_preparations WHERE id LIKE 'duplicate-%'")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void v21MigratesExistingLegacyMarketingSourceWithoutAuthorityLoss() throws Exception {
        String schema = "v21_legacy_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV20 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").target("20").load();
        throughV20.migrate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("SET session_replication_role = replica");
            statement.execute("""
                INSERT INTO marketing_source_snapshots (
                    id, project_id, source_market_seed_snapshot_id, selection_id, concept_id,
                    schema_version, snapshot_hash, snapshot_json, created_by_user_id, finalized_at,
                    created_at, updated_at, version
                ) VALUES ('legacy-source', 41, 'legacy-seed', 13, 'legacy-concept', '2.0',
                    'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '{}', 7,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
            statement.execute("SET session_replication_role = origin");
        }
        Flyway latest = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isOne();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet result = statement.executeQuery("""
                    SELECT source_type, selection_id, concept_id, portfolio_selection_id, portfolio_concept_id
                    FROM marketing_source_snapshots WHERE id = 'legacy-source'
                    """)) {
                result.next();
                assertThat(result.getString("source_type")).isEqualTo("LEGACY");
                assertThat(result.getLong("selection_id")).isEqualTo(13L);
                assertThat(result.getString("concept_id")).isEqualTo("legacy-concept");
                assertThat(result.getObject("portfolio_selection_id")).isNull();
                assertThat(result.getObject("portfolio_concept_id")).isNull();
            }
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

    private int constraintCount(Connection connection, String schema, String table, String constraint) throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*) from information_schema.table_constraints
            where constraint_schema = ? and table_name = ? and constraint_name = ?
            """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, constraint);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int indexCount(Connection connection, String schema, String table, String index) throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*) from pg_indexes
            where schemaname = ? and tablename = ? and indexname = ?
            """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, index);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
