package com.aivle.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("postgres")
class PostgreSqlContainerSmokeTests extends PostgreSqlIntegrationTestSupport {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table users, stored_files restart identity cascade"
        );
    }

    @Test
    void startsPinnedPostgreSqlAndAppliesAllMigrations() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        String timezone = jdbcTemplate.queryForObject("show timezone", String.class);
        String encoding = jdbcTemplate.queryForObject(
            "show server_encoding",
            String.class
        );

        assertThat(version).contains("PostgreSQL 17.10");
        assertThat(timezone).isEqualTo("UTC");
        assertThat(encoding).isEqualTo("UTF8");
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().current().getVersion().getVersion())
            .isEqualTo("1");
        System.out.printf(
            "R7_PG_FRESH postgres=\"%s\" flywayLatest=1 "
                + "applied=1 applicationContext=PASS "
                + "ddlAutoValidate=PASS%n",
            version
        );
    }

    @Test
    void persistsKoreanTextEnumAndTimestampOnPostgreSql() {
        jdbcTemplate.update("""
            insert into users (
                username, email, password_hash, name, role, status, failed_login_count,
                created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, 0, current_timestamp, current_timestamp, 0)
            """,
            "phase2-smoke",
            "phase2-smoke@example.com",
            "not-a-real-password-hash",
            "한글 사용자",
            "USER",
            "ACTIVE"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap("""
            select name, role, created_at
            from users
            where email = 'phase2-smoke@example.com'
            """);
        assertThat(row.get("name")).isEqualTo("한글 사용자");
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("created_at")).isNotNull();
    }
}
