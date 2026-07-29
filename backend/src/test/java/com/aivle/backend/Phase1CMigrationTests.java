package com.aivle.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Phase1CMigrationTests {
    @Autowired JdbcClient jdbcClient;

    @Test
    void v4AddsExecutionControlAndResultUniqueness() {
        assertThat(columnExists("analysis_jobs", "attempt_count")).isTrue();
        assertThat(columnExists("analysis_jobs", "next_attempt_at")).isTrue();
        assertThat(columnExists("analysis_jobs", "claimed_at")).isTrue();
        assertThat(columnExists("analysis_jobs", "claimed_by")).isTrue();
        assertThat(columnExists("analysis_jobs", "heartbeat_at")).isTrue();
        assertThat(columnExists("analysis_jobs", "claim_token")).isTrue();
        assertThat(columnExists("structured_plan_sections", "display_order")).isTrue();
        // V4가 추가한 uk_plan_source_document_version은 V13(피드백 루프)에서 의도적으로 삭제됐다.
        // 파생 plan 버전이 문서 버전을 공유하며, UPLOAD 유일성은 서비스 계층이 보장한다.
        assertThat(constraintExists("uk_plan_source_document_version")).isFalse();
    }

    @Test
    void allFourMigrationsApply() {
        Integer count = jdbcClient.sql(
            "select count(*) from flyway_schema_history where success = true"
        ).query(Integer.class).single();
        assertThat(count).isGreaterThanOrEqualTo(4);
    }

    @Test
    void v1V2AndV3ChecksumsRemainUnchanged() throws Exception {
        assertThat(hash("db/migration/V1__create_core_tables.sql"))
            .isEqualTo("15011e63b8c3e1aeed4da3839660857d3eebdc93d47aa3f58ca06b27c96d1836");
        assertThat(hash("db/migration/V2__create_simulation_report_tables.sql"))
            .isEqualTo("a9b13c0830b3ace4f2d166333ce0738fe33ef6b88d02f735e47bb4a028b5fc47");
        assertThat(hash("db/migration/V3__extend_document_processing_metadata.sql"))
            .isEqualTo("2259c4bbda17bb95c885b81e55c94973a1ba70ec03e6aa5af4b5b000c7a3067e");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcClient.sql("""
            select count(*) from information_schema.columns
            where lower(table_name) = :table and lower(column_name) = :column
            """)
            .param("table", table)
            .param("column", column)
            .query(Integer.class)
            .single();
        return count == 1;
    }

    private boolean constraintExists(String constraint) {
        Integer count = jdbcClient.sql("""
            select count(*) from information_schema.table_constraints
            where lower(constraint_name) = :constraint
            """)
            .param("constraint", constraint)
            .query(Integer.class)
            .single();
        return count == 1;
    }

    private String hash(String path) throws Exception {
        byte[] bytes = new ClassPathResource(path).getInputStream().readAllBytes();
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }
}
