package com.aivle.backend.analysis.financial;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import com.aivle.backend.analysis.financial.repository.FinancialAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FinancialAnalysisApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired EntityManager entityManager;
    @Autowired FinancialAnalysisRepository analyses;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:h2:mem:financial-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @BeforeEach
    void fixture() {
        jdbc.sql("""
            insert into users (
              id, username, email, password_hash, name, role, status, failed_login_count,
              security_version, created_at, updated_at, version
            ) values
              (8101, 'financial-owner', 'financial-owner@example.com', 'hash', '재무 소유자',
               'USER', 'ACTIVE', 0, 0, current_timestamp, current_timestamp, 0),
              (8102, 'financial-other', 'financial-other@example.com', 'hash', '다른 사용자',
               'USER', 'ACTIVE', 0, 0, current_timestamp, current_timestamp, 0)
            """).update();
        jdbc.sql("""
            insert into projects (
              id, owner_id, title, description, industry_category, stage, status,
              created_at, updated_at, version
            ) values (
              8201, 8101, '재무 검증 프로젝트', '구독과 판매를 검증합니다.', 'SaaS',
              'FINANCIAL', 'ACTIVE', current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into stored_files (
              id, storage_type, storage_key, original_filename, stored_filename,
              extension, mime_type, size_bytes, checksum_sha256, status, encrypted,
              created_at, updated_at, version
            ) values (
              8301, 'LOCAL', 'financial-source', 'plan.docx', 'stored.docx', 'docx',
              'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
              100, repeat('a', 64), 'AVAILABLE', false,
              current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into project_documents (
              id, project_id, document_type, current_version, status,
              created_at, updated_at, version
            ) values (8401, 8201, 'BUSINESS_PLAN', 1, 'ACTIVE',
              current_timestamp, current_timestamp, 0)
            """).update();
        jdbc.sql("""
            insert into document_versions (
              id, document_id, version_number, stored_file_id, parse_status,
              uploaded_by, uploaded_at, created_at, updated_at, version
            ) values (8501, 8401, 1, 8301, 'SUCCEEDED', 8101, current_timestamp,
              current_timestamp, current_timestamp, 0)
            """).update();
        jdbc.sql("""
            insert into structured_plans (
              id, project_id, source_document_version_id, version_number, status,
              completion_rate, confirmed_by_user, confirmed_at, confirmed_by_user_id,
              created_at, updated_at, version
            ) values (8601, 8201, 8501, 1, 'CONFIRMED', 100, true, current_timestamp, 8101,
              current_timestamp, current_timestamp, 0)
            """).update();
        jdbc.sql("""
            insert into analysis_jobs (
              id, project_id, job_type, status, progress, retry_count, attempt_count,
              source_structured_plan_id, created_at, updated_at, version
            ) values
              (8701, 8201, 'LEGAL_REVIEW', 'SUCCEEDED', 100, 0, 1, 8601,
               current_timestamp, current_timestamp, 0),
              (8702, 8201, 'FEASIBILITY_ANALYSIS', 'SUCCEEDED', 100, 0, 1, 8601,
               current_timestamp, current_timestamp, 0)
            """).update();
        jdbc.sql("""
            insert into legal_reviews (
              id, project_id, analysis_job_id, structured_plan_id, source_document_version_id,
              version_number, status, risk_level, summary, disclaimer, provider, model_name,
              prompt_version, prompt_hash, raw_result_hash, input_snapshot_json, completed_at,
              created_at, updated_at, version
            ) values (
              8801, 8201, 8701, 8601, 8501, 1, 'COMPLETED', 'LOW', '법률 요약',
              '법률 자문 아님', 'mock', 'mock', 'v1', repeat('b', 64), repeat('c', 64),
              '{}', current_timestamp, current_timestamp, current_timestamp, 0
            )
            """).update();
        jdbc.sql("""
            insert into feasibility_assessments (
              id, project_id, analysis_job_id, structured_plan_id, legal_review_id,
              source_document_version_id, version_number, status, verdict, overall_score,
              confidence, summary, key_strengths_json, key_risks_json,
              validation_tasks_summary_json, disclaimer, provider, model_name, prompt_version,
              catalog_version, prompt_hash, input_hash, result_hash, input_snapshot_json,
              completed_at, created_at, updated_at, version
            ) values (
              8901, 8201, 8702, 8601, 8801, 8501, 1, 'COMPLETED', 'PROMISING', 80, 'HIGH',
              '사업 타당성 완료', '[]', '[]', '[]', '투자 자문 아님', 'mock', 'mock',
              'v1', 'v1', repeat('d', 64), repeat('e', 64), repeat('f', 64), '{}',
              current_timestamp, current_timestamp, current_timestamp, 0
            )
            """).update();
    }

    @Test
    void sourceCrudRunDuplicateAndSoftDeletePreserveContractAndAudit() throws Exception {
        mockMvc.perform(owner(get("/api/v1/projects/8201/financial-analysis/source")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.feasibilityAssessmentId").value(8901));

        String created = mockMvc.perform(owner(post(path()))
                .header("X-Request-Id", "financial-create")
                .contentType(MediaType.APPLICATION_JSON).content(body("초안", 12)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.summary.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.data.summary.id");

        mockMvc.perform(owner(get(path()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].resultJson").doesNotExist());
        mockMvc.perform(owner(get(path() + "/{id}", id))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assumptions.unitPrice").value(10000));
        mockMvc.perform(owner(patch(path() + "/{id}", id))
                .contentType(MediaType.APPLICATION_JSON).content(body("수정 초안", 24)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.analysisPeriodMonths").value(24));
        mockMvc.perform(owner(post(path() + "/{id}/run", id))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.resultJson", containsString("totalRevenue")));
        mockMvc.perform(owner(post(path() + "/{id}/duplicate", id))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.summary.status").value("DRAFT"));
        mockMvc.perform(owner(delete(path() + "/{id}", id))).andExpect(status().isNoContent());
        mockMvc.perform(owner(get(path() + "/{id}", id))).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("FINANCIAL_ANALYSIS_NOT_FOUND"));

        for (String event : new String[] {
            "FINANCIAL_ANALYSIS_CREATED", "FINANCIAL_ANALYSIS_UPDATED",
            "FINANCIAL_ANALYSIS_COMPLETED", "FINANCIAL_ANALYSIS_DUPLICATED",
            "FINANCIAL_ANALYSIS_DELETED"
        }) {
            Integer count = jdbc.sql("select count(*) from audit_events where event_type = :event")
                .param("event", event).query(Integer.class).single();
            org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
        }
        String metadata = jdbc.sql("""
            select metadata_json from audit_events
            where event_type = 'FINANCIAL_ANALYSIS_COMPLETED'
            """).query(String.class).single();
        org.assertj.core.api.Assertions.assertThat(metadata)
            .contains("financialAnalysisId").doesNotContain("assumptions").doesNotContain("resultJson");
    }

    @Test
    void sourceRejectsAnIncompleteFeasibilityAssessmentWithoutInventingDefaults() throws Exception {
        jdbc.sql("update feasibility_assessments set status = 'NEEDS_VALIDATION' where id = 8901").update();
        entityManager.clear();
        mockMvc.perform(owner(get("/api/v1/projects/8201/financial-analysis/source")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("FINANCIAL_FEASIBILITY_REQUIRED"));
    }

    @Test
    void authenticationOwnershipAndProjectScopedIdsAreEnforced() throws Exception {
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        mockMvc.perform(other(get(path()))).andExpect(status().isForbidden());
        String created = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON).content(body("소유권", 12)))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.data.summary.id");
        mockMvc.perform(other(get(path() + "/{id}", id))).andExpect(status().isForbidden());
        mockMvc.perform(owner(get("/api/v1/projects/999999/financial-analyses/{id}", id)))
            .andExpect(status().isForbidden());
    }

    @Test
    void maintenanceBlocksEveryWriteButAllowsListAndDetail() throws Exception {
        String created = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON).content(body("점검 전", 12)))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.data.summary.id");
        jdbc.sql("""
            merge into service_settings (setting_key, setting_value, updated_by, updated_at)
            key(setting_key) values ('MAINTENANCE_MODE', 'true', 8101, current_timestamp)
            """).update();

        mockMvc.perform(owner(get(path()))).andExpect(status().isOk());
        mockMvc.perform(owner(get(path() + "/{id}", id))).andExpect(status().isOk());
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            owner(post(path()).contentType(MediaType.APPLICATION_JSON).content(body("차단", 12))),
            owner(patch(path() + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body("차단", 12))),
            owner(post(path() + "/{id}/run", id)),
            owner(post(path() + "/{id}/duplicate", id)),
            owner(delete(path() + "/{id}", id))
        }) {
            mockMvc.perform(request).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("MAINTENANCE_MODE_ENABLED"));
        }
    }

    @Test
    void completionAdvancesFinancialStageWithoutRegressingLaterStage() throws Exception {
        String created = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON).content(body("단계", 12)))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.data.summary.id");
        mockMvc.perform(owner(post(path() + "/{id}/run", id))).andExpect(status().isOk());
        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(stage()).isEqualTo("PERSONA_CONFIGURATION");

        jdbc.sql("update projects set stage = 'REPORT' where id = 8201").update();
        entityManager.clear();
        String second = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON).content(body("후속 단계", 12)))
            .andReturn().getResponse().getContentAsString();
        Number secondId = JsonPath.read(second, "$.data.summary.id");
        mockMvc.perform(owner(post(path() + "/{id}/run", secondId))).andExpect(status().isOk());
        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(stage()).isEqualTo("REPORT");
    }

    private String stage() {
        return jdbc.sql("select stage from projects where id = 8201").query(String.class).single();
    }
    private String path() { return "/api/v1/projects/8201/financial-analyses"; }
    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 8101).header("X-User-Role", "USER");
    }
    private MockHttpServletRequestBuilder other(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 8102).header("X-User-Role", "USER");
    }
    private String body(String title, int period) {
        return """
            {
              "title":"%s","analysisPeriodMonths":%d,
              "assumptions":{
                "revenueModel":"ONE_TIME","unitPrice":10000,"monthlySalesVolume":100,
                "monthlyGrowthRate":0,"unitVariableCost":3000,"paymentFeeRate":3,
                "otherVariableCostPerUnit":500,"monthlyLaborCost":200000,
                "monthlyMarketingCost":100000,"monthlyInfrastructureCost":0,
                "monthlyRentCost":0,"monthlyOtherFixedCost":0,
                "initialDevelopmentCost":500000,"initialEquipmentCost":0,
                "initialMarketingCost":0,"initialOtherCost":0,
                "monthlySubscriptionPrice":null,"initialSubscribers":null,
                "monthlyNewSubscribers":null,"monthlyChurnRate":0
              },
              "scenarios":[
                {"code":"CONSERVATIVE","label":"보수","salesVolumeAdjustment":-20,"priceAdjustment":0,"variableCostAdjustment":10,"fixedCostAdjustment":10},
                {"code":"BASE","label":"기준","salesVolumeAdjustment":0,"priceAdjustment":0,"variableCostAdjustment":0,"fixedCostAdjustment":0},
                {"code":"OPTIMISTIC","label":"낙관","salesVolumeAdjustment":20,"priceAdjustment":0,"variableCostAdjustment":-5,"fixedCostAdjustment":-5}
              ]
            }
            """.formatted(title, period);
    }
}
