package com.aivle.backend.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("postgres")
class PostgreSqlConstraintTests extends PostgreSqlIntegrationTestSupport {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table users, stored_files restart identity cascade"
        );
    }

    @Test
    void allowsOnlyOneLogicalActiveBusinessPlanPerProject() {
        insertUserAndProject();
        insertDocument(1, "BUSINESS_PLAN", "ACTIVE", null);

        assertThatThrownBy(() ->
            insertDocument(2, "BUSINESS_PLAN", "ACTIVE", null)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replacedAndDeletedDocumentsDoNotOccupyActiveConstraint() {
        insertUserAndProject();
        insertDocument(1, "BUSINESS_PLAN", "REPLACED", null);
        insertDocument(2, "BUSINESS_PLAN", "ACTIVE", null);
        insertDocument(3, "BUSINESS_PLAN", "ACTIVE", "2026-01-01 00:00:00");

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from project_documents",
            Integer.class
        )).isEqualTo(3);
    }

    @Test
    void preservesAllFiveStatusesAndRejectsUnknownDatabaseValue() {
        insertStructuredPlanFixture();
        String[] statuses = {"PRESENT", "MISSING", "PARTIAL", "INVALID", "UNKNOWN"};
        String[] codes = {
            "BUSINESS_OVERVIEW",
            "MARKET_SIZE",
            "TARGET_CUSTOMER",
            "COMPETITIVE_ANALYSIS",
            "PRODUCT_SERVICE"
        };
        for (int index = 0; index < statuses.length; index++) {
            insertSection(index + 1, codes[index], statuses[index]);
        }
        assertThat(jdbcTemplate.queryForList("""
            select item_status
            from structured_plan_sections
            order by display_order
            """, String.class)).containsExactly(statuses);

        assertThatThrownBy(() ->
            insertSection(6, "BUSINESS_MODEL", "UNSUPPORTED")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonCanonicalSectionCode() {
        insertStructuredPlanFixture();
        assertThatThrownBy(() ->
            insertSection(1, "EXECUTIVE_SUMMARY", "PRESENT")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyIsUniqueWithinProjectAndJobType() {
        insertUserAndProject();
        insertJob(1, "same-key");
        assertThatThrownBy(() ->
            insertJob(2, "same-key")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUserAndProject() {
        jdbcTemplate.update("""
            insert into users (
                id, username, email, password_hash, name, role, status, failed_login_count,
                created_at, updated_at, version
            ) values (
                1, 'constraintuser', 'constraint@example.com', 'hash', '제약 테스트', 'USER', 'ACTIVE', 0,
                current_timestamp, current_timestamp, 0
            )
            """);
        jdbcTemplate.update("""
            insert into projects (
                id, owner_id, title, stage, status, created_at, updated_at, version
            ) values (
                1, 1, '제약 프로젝트', 'DOCUMENT', 'ACTIVE',
                current_timestamp, current_timestamp, 0
            )
            """);
    }

    private void insertDocument(long id, String type, String status, String deletedAt) {
        jdbcTemplate.update("""
            insert into project_documents (
                id, project_id, document_type, current_version, status,
                created_at, updated_at, deleted_at, version
            ) values (?, 1, ?, 0, ?, current_timestamp, current_timestamp, ?::timestamp, 0)
            """, id, type, status, deletedAt);
    }

    private void insertStructuredPlanFixture() {
        insertUserAndProject();
        jdbcTemplate.update("""
            insert into stored_files (
                id, storage_type, storage_key, original_filename, stored_filename,
                extension, mime_type, size_bytes, checksum_sha256, status, encrypted,
                created_at, updated_at, version
            ) values (
                1, 'LOCAL', 'constraint-file', 'fixture.docx', 'stored.docx', 'docx',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                10, repeat('b', 64), 'ACTIVE', false,
                current_timestamp, current_timestamp, 0
            )
            """);
        insertDocument(1, "BUSINESS_PLAN", "ACTIVE", null);
        jdbcTemplate.update("update project_documents set current_version = 1 where id = 1");
        jdbcTemplate.update("""
            insert into document_versions (
                id, document_id, version_number, stored_file_id, parse_status,
                uploaded_by, uploaded_at, created_at, updated_at, version
            ) values (
                1, 1, 1, 1, 'SUCCEEDED', 1, current_timestamp,
                current_timestamp, current_timestamp, 0
            )
            """);
        jdbcTemplate.update("""
            insert into structured_plans (
                id, project_id, source_document_version_id, version_number,
                status, completion_rate, confirmed_by_user,
                created_at, updated_at, version
            ) values (
                1, 1, 1, 1, 'DRAFT', 100, false,
                current_timestamp, current_timestamp, 0
            )
            """);
    }

    private void insertSection(int order, String code, String status) {
        jdbcTemplate.update("""
            insert into structured_plan_sections (
                structured_plan_id, section_type, title, section_code,
                item_status, display_order, created_at, updated_at, version
            ) values (
                1, 'OVERVIEW', ?, ?, ?, ?, current_timestamp, current_timestamp, 0
            )
            """, code, code, status, order);
    }

    private void insertJob(long id, String key) {
        jdbcTemplate.update("""
            insert into analysis_jobs (
                id, project_id, job_type, status, progress, retry_count,
                idempotency_key, attempt_count,
                created_at, updated_at, version
            ) values (
                ?, 1, 'DOCUMENT_PARSE', 'QUEUED', 0, 0, ?, 0,
                current_timestamp, current_timestamp, 0
            )
            """, id, key);
    }
}
