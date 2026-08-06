package com.aivle.backend.analysis.feasibility;

import com.aivle.backend.analysis.feasibility.application.*;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.job.runner.JobClaimService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FeasibilityVerticalSliceIntegrationTests {
    @Autowired JdbcClient jdbc;
    @Autowired FeasibilityCommandService commands;
    @Autowired FeasibilityQueryService queries;
    @Autowired JobClaimService claims;
    @Autowired FeasibilityJobExecutor executor;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:h2:mem:phase9-feasibility;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @BeforeEach
    void fixture() {
        jdbc.sql("""
            insert into users (
              id, username, email, password_hash, name, role, status, failed_login_count,
              security_version, created_at, updated_at, version
            ) values
              (100, 'owneruser', 'owner@example.com', 'hash', 'owner', 'USER', 'ACTIVE', 0,
               0, current_timestamp, current_timestamp, 0),
              (200, 'otheruser', 'other@example.com', 'hash', 'other', 'USER', 'ACTIVE', 0,
               0, current_timestamp, current_timestamp, 0)
            """).update();
        jdbc.sql("""
            insert into projects (
              id, owner_id, title, stage, status, created_at, updated_at, version
            ) values (
              10, 100, '사업 아이디어', 'FEASIBILITY', 'ACTIVE',
              current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into stored_files (
              id, storage_type, storage_key, original_filename, stored_filename,
              extension, mime_type, size_bytes, checksum_sha256, status, encrypted,
              created_at, updated_at, version
            ) values (
              20, 'LOCAL', 'phase9-source', 'plan.docx', 'stored.docx', 'docx',
              'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
              100, repeat('a', 64), 'AVAILABLE', false,
              current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into project_documents (
              id, project_id, document_type, current_version, status,
              created_at, updated_at, version
            ) values (
              30, 10, 'BUSINESS_PLAN', 1, 'ACTIVE',
              current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into document_versions (
              id, document_id, version_number, stored_file_id, parse_status,
              uploaded_by, uploaded_at, created_at, updated_at, version
            ) values (
              40, 30, 1, 20, 'SUCCEEDED', 100, current_timestamp,
              current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into structured_plans (
              id, project_id, source_document_version_id, version_number, status,
              completion_rate, confirmed_by_user, confirmed_at, confirmed_by_user_id,
              created_at, updated_at, version
            ) values (
              50, 10, 40, 1, 'CONFIRMED', 100, true, current_timestamp, 100,
              current_timestamp, current_timestamp, 0
            )
            """).update();
        String[] codes = {
            "BUSINESS_OVERVIEW", "MARKET_SIZE", "TARGET_CUSTOMER",
            "COMPETITIVE_ANALYSIS", "PRODUCT_SERVICE", "BUSINESS_MODEL",
            "COST_PROFITABILITY", "SALES_GOALS_FINANCIAL_PROJECTIONS",
            "TECHNOLOGY_PRODUCTION", "LEGAL_PERMITS", "SCHEDULE_RISK", "EVIDENCE_LIST"
        };
        String[] types = {
            "OVERVIEW", "MARKET", "TARGET_CUSTOMER", "COMPETITION",
            "PRODUCT_SERVICE", "BUSINESS_MODEL", "FINANCIAL", "FINANCIAL",
            "TECHNOLOGY_OPERATION", "LEGAL_REGULATION", "RISK", "EVIDENCE"
        };
        for (int index = 0; index < codes.length; index++) {
            jdbc.sql("""
                insert into structured_plan_sections (
                  id, structured_plan_id, section_type, title, source_text,
                  section_code, item_status, evidence_json, source_block_references_json,
                  display_order, created_at, updated_at, version
                ) values (
                  :id, 50, :type, :title, :content, :code, 'PRESENT', '[]', '[]',
                  :displayOrder, current_timestamp, current_timestamp, 0
                )
                """)
                .param("id", 1000 + index)
                .param("type", types[index])
                .param("title", codes[index])
                .param("content", "사용자가 확정한 계획 내용 " + codes[index])
                .param("code", codes[index])
                .param("displayOrder", index + 1)
                .update();
        }
        jdbc.sql("""
            insert into analysis_jobs (
              id, project_id, job_type, status, progress, retry_count, attempt_count,
              source_structured_plan_id, created_at, updated_at, version
            ) values (
              60, 10, 'LEGAL_REVIEW', 'SUCCEEDED', 100, 0, 1, 50,
              current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into legal_reviews (
              id, project_id, analysis_job_id, structured_plan_id,
              source_document_version_id, version_number, status, risk_level,
              summary, disclaimer, provider, model_name, prompt_version,
              prompt_hash, raw_result_hash, input_snapshot_json, completed_at,
              created_at, updated_at, version
            ) values (
              70, 10, 60, 50, 40, 1, 'NEEDS_REVIEW', 'HIGH',
              '확인이 필요한 법률 위험이 있습니다.', '사전검토 한계', 'mock',
              'mock-legal', 'legal-review-v1', repeat('b', 64), repeat('c', 64),
              '{}', current_timestamp, current_timestamp, current_timestamp, 0
            )
            """).update();
    }

    @AfterEach
    void cleanup() {
        jdbc.sql("update analysis_jobs set source_legal_review_id = null").update();
        for (String table : new String[] {
            "audit_events", "feasibility_validation_tasks", "feasibility_dimension_results",
            "feasibility_assessments", "legal_review_questions", "legal_findings",
            "legal_reviews", "missing_fields", "structured_plan_sections", "analysis_jobs",
            "structured_plans", "document_versions", "project_documents", "stored_files",
            "projects", "refresh_tokens", "users"
        }) {
            jdbc.sql("delete from " + table).update();
        }
    }

    @Test
    void runsPersistedMockAssessmentAndRecoversIdempotentlyWithOwnership() {
        var accepted = commands.start(100L, 10L);
        assertThat(accepted.status().name()).isEqualTo("QUEUED");
        assertThat(accepted.legalReviewId()).isEqualTo(70L);
        assertThatThrownBy(() -> commands.start(100L, 10L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("동일한 분석");

        var claim = claims.claimOne(accepted.jobId()).orElseThrow();
        assertThat(claim.jobType()).isEqualTo(JobType.FEASIBILITY_ANALYSIS);
        executor.execute(claim);

        var result = queries.latest(100L, 10L);
        assertThat(result.dimensions()).hasSize(10);
        assertThat(result.validationTasks()).extracting(
            item -> item.code()).contains(
                "VERIFY_MARKET_SOURCES", "VALIDATE_FINANCIAL_ASSUMPTIONS",
                "RESOLVE_LEGAL_CONSTRAINTS");
        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.verdict().name()).isEqualTo("CONDITIONAL");
        assertThat(commands.start(100L, 10L).assessmentId()).isEqualTo(result.assessmentId());
        assertThatThrownBy(() -> queries.latest(200L, 10L))
            .isInstanceOf(BusinessException.class);
    }
}
