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
    void appliesAndValidatesBaselineAndAdditiveWorkspaceMigrationOnEmptyPostgreSqlSchema() throws Exception {
        String schema = "baseline_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
        flyway.validate();

        var appliedVersions = Arrays.stream(flyway.info().applied())
            .filter(info -> info.getVersion() != null)
            .map(info -> info.getVersion().getVersion())
            .toList();

        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");

        try (Connection connection = DriverManager.getConnection(
                 POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);

            assertTables(connection, schema,
                "users", "projects", "stored_files", "document_versions",
                "analysis_jobs", "financial_analyses", "service_settings",
                "task_runs", "task_attempts", "task_results",
                "idea_sources", "idea_versions", "idea_origin_versions",
                "legal_precheck_runs", "legal_precheck_versions", "legal_guardrail_sets",
                "concept_eligibility_batches", "concept_drafts", "concept_versions",
                "persona_studies", "marketing_workspaces", "final_reports",
                "idea_conversations", "idea_messages", "idea_attachments",
                "opportunity_brief_versions", "opportunity_field_values",
                "regulatory_boundary_runs", "regulatory_boundary_versions",
                "boundary_rules", "boundary_evidence", "boundary_questions", "job_events",
                "concept_exploration_batches", "concept_slots", "concept_attempts",
                "concept_origin_validations", "concept_legal_assessments", "concept_rule_traces",
                "exploration_concepts");

            // Final effects formerly supplied by Java V5 and V10.
            assertThat(columnNullable(connection, schema, "users", "email")).isTrue();
            assertThat(columnNullable(connection, schema, "users", "username")).isFalse();
            assertThat(indexExists(connection, schema, "uk_users_username")).isTrue();
            assertThat(indexExists(connection, schema, "uk_active_business_plan_per_project")).isTrue();
            assertThat(checkConstraintExists(connection, schema, "ck_structured_section_code")).isTrue();
            assertThat(checkConstraintExists(connection, schema, "ck_structured_item_status")).isTrue();

            assertThat(columnNullable(connection, schema, "financial_analyses", "version_number")).isFalse();
            assertThat(columnNullable(connection, schema, "financial_analyses", "title")).isFalse();
            assertThat(columnNullable(connection, schema, "financial_analyses", "analysis_period_months")).isFalse();
            assertThat(columnNullable(connection, schema, "financial_analyses", "assumptions_json")).isFalse();
            assertThat(columnNullable(connection, schema, "concept_eligibility_batches", "retryable")).isFalse();
            assertThat(indexExists(connection, schema, "idx_financial_journey_concept")).isTrue();
            assertThat(foreignKeyExists(connection, schema, "task_results", "task_attempt_id")).isTrue();
            assertThat(foreignKeyExists(connection, schema, "concept_versions", "eligibility_batch_id")).isTrue();
            assertThat(foreignKeyExists(connection, schema, "job_events", "task_run_id")).isTrue();
            assertThat(indexExists(connection, schema, "idx_job_event_replay")).isTrue();
        }
    }

    @Test
    void upgradesAnExistingV1SchemaWithOnlyTheAdditiveWorkspaceMigration() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway baseline = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema)
            .schemas(schema)
            .locations("classpath:db/migration")
            .target("1")
            .load();

        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(1);

        Flyway upgrade = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load();

        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("5");
    }

    @Test
    void upgradesAnExistingV2SchemaThroughV4Hardening() throws Exception {
        String schema = "upgrade_v2_" + UUID.randomUUID().toString().replace("-", "");
        Flyway baseline = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").target("2").load();
        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(2);
        Flyway upgrade = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("5");
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(columnNullable(connection, schema, "idea_messages", "message_type")).isFalse();
            assertThat(columnNullable(connection, schema, "opportunity_field_values", "user_confirmed")).isFalse();
            assertThat(foreignKeyExists(connection, schema, "opportunity_field_values", "source_message_id")).isTrue();
            assertThat(foreignKeyExists(connection, schema, "opportunity_field_values", "source_attachment_id")).isTrue();
        }
    }

    @Test
    void upgradesAnExistingV3SchemaWithOnlyV4BoundaryContract() throws Exception {
        String schema = "upgrade_v3_" + UUID.randomUUID().toString().replace("-", "");
        Flyway baseline = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").target("3").load();
        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(3);
        Flyway upgrade = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("5");
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(columnNullable(connection, schema, "regulatory_boundary_versions", "brief_snapshot_hash")).isFalse();
            assertThat(columnNullable(connection, schema, "boundary_evidence", "content_hash")).isFalse();
            assertThat(columnNullable(connection, schema, "boundary_rules", "normalized_requirement")).isFalse();
            assertThat(columnNullable(connection, schema, "boundary_questions", "answer_type")).isFalse();
            assertThat(indexExists(connection, schema, "uk_boundary_evidence_content")).isTrue();
        }
    }

    @Test
    void upgradesAnExistingV4SchemaWithOnlyV5ConceptCore() throws Exception {
        String schema = "upgrade_v4_" + UUID.randomUUID().toString().replace("-", "");
        Flyway baseline = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").target("4").load();
        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(4);
        Flyway upgrade = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("5");
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(foreignKeyExists(connection, schema, "concept_exploration_batches", "brief_version_id")).isTrue();
            assertThat(foreignKeyExists(connection, schema, "concept_exploration_batches", "boundary_version_id")).isTrue();
            assertThat(indexExists(connection, schema, "idx_concept_slots_batch")).isTrue();
            assertThat(indexExists(connection, schema, "idx_exploration_concepts_public")).isTrue();
        }
    }

    private void assertTables(Connection connection, String schema, String... tables) throws Exception {
        for (String table : tables) {
            try (var statement = connection.prepareStatement("""
                select count(*) from information_schema.tables
                where table_schema = ? and table_name = ?
                """)) {
                statement.setString(1, schema);
                statement.setString(2, table);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    assertThat(result.getInt(1)).as("table %s", table).isEqualTo(1);
                }
            }
        }
    }

    private boolean columnNullable(Connection connection, String schema, String table, String column)
        throws Exception {
        try (var statement = connection.prepareStatement("""
            select is_nullable from information_schema.columns
            where table_schema = ? and table_name = ? and column_name = ?
            """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).as("column %s.%s", table, column).isTrue();
                return "YES".equals(result.getString(1));
            }
        }
    }

    private boolean indexExists(Connection connection, String schema, String index) throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*) from pg_indexes where schemaname = ? and indexname = ?
            """)) {
            statement.setString(1, schema);
            statement.setString(2, index);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private boolean checkConstraintExists(Connection connection, String schema, String constraint) throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*) from information_schema.table_constraints
            where constraint_schema = ? and constraint_name = ? and constraint_type = 'CHECK'
            """)) {
            statement.setString(1, schema);
            statement.setString(2, constraint);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private boolean foreignKeyExists(Connection connection, String schema, String table, String column)
        throws Exception {
        try (var statement = connection.prepareStatement("""
            select count(*)
            from information_schema.key_column_usage k
            join information_schema.table_constraints c
              on c.constraint_schema = k.constraint_schema and c.constraint_name = k.constraint_name
            where k.table_schema = ? and k.table_name = ? and k.column_name = ?
              and c.constraint_type = 'FOREIGN KEY'
            """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }
}
