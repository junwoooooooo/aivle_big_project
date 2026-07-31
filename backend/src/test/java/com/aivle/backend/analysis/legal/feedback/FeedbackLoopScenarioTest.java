package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.application.LegalReviewJobExecutor;
import com.aivle.backend.analysis.legal.application.LegalReviewPolicy;
import com.aivle.backend.analysis.legal.entity.*;
import com.aivle.backend.analysis.legal.repository.*;
import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.TestDocxFactory;
import com.aivle.backend.document.application.DocumentCommandService;
import com.aivle.backend.document.application.DocumentUploadCommand;
import com.aivle.backend.document.application.processing.DocumentParseJobExecutor;
import com.aivle.backend.document.entity.PlanOrigin;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.document.repository.*;
import com.aivle.backend.document.structure.*;
import com.aivle.backend.file.validation.BusinessPlanDocxPolicy;
import com.aivle.backend.integration.ai.AiServiceClient;
import com.aivle.backend.integration.ai.document.*;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.integration.ai.legal.LegalReviewAiRequest;
import com.aivle.backend.integration.ai.legal.MockLegalReviewAiClient;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaimService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §5 E2E 수용 시나리오 (프레시락 미니). Mock provider 기반 결정론 테스트.
 * 시나리오 1~5가 곧 완료 기준이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FeedbackLoopScenarioTest.FeedbackAiConfiguration.class)
class FeedbackLoopScenarioTest {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final String AD_SENTENCE = "악취 30% 개선을 핵심 광고 카피로 사용한다.";
    private static final byte[] FRESH_LOCK_DOCX = TestDocxFactory.document(
        "프레시락 미니 사업계획서",
        "활성탄 필터 신선보관 용기",
        "온라인 판매 중심 유통 계획",
        "출시 일정과 리스크"
    );
    private static final Pattern ACTION_PATTERN = Pattern.compile("^(.+?)\\s*\\(([^()]+)\\)$");

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired DocumentCommandService documentCommandService;
    @Autowired JobClaimService claimService;
    @Autowired DocumentParseJobExecutor parseExecutor;
    @Autowired LegalReviewJobExecutor legalExecutor;
    @Autowired StructuredPlanRepository planRepository;
    @Autowired StructuredPlanSectionRepository sectionRepository;
    @Autowired LegalReviewRepository reviewRepository;
    @Autowired LegalFindingRepository findingRepository;
    @Autowired LegalReviewQuestionRepository questionRepository;
    @Autowired ReviewCycleRepository cycleRepository;
    @Autowired RevisionRequestRepository revisionRequestRepository;
    @Autowired RevisionSuggestionRepository suggestionRepository;
    @Autowired ConfirmedFactRepository factRepository;
    @Autowired PublicationRepository publicationRepository;
    @Autowired MockLegalReviewAiClient mockClient;
    @Autowired JdbcClient jdbcClient;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:h2:mem:feedback-loop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("app.file-storage.root", () -> STORAGE_ROOT.toString());
    }

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of(
            "publications",
            "revision_suggestions",
            "revision_requests",
            "confirmed_facts",
            "legal_review_questions",
            "legal_findings",
            "legal_reviews",
            "review_cycles",
            "audit_events",
            "refresh_tokens",
            "missing_fields",
            "structured_plan_sections",
            "analysis_jobs",
            "structured_plans",
            "document_versions",
            "project_documents",
            "stored_files",
            "projects",
            "users"
        )) {
            jdbcClient.sql("delete from " + table).update();
        }
        mockClient.reset();
    }

    // ── 시나리오 1: 첫 검토 ───────────────────────────────────────────────

    @Test
    void scenario1_fullReviewProducesTriageItemsAndNeedsAction() throws Exception {
        Fixture fx = confirmedFixture();

        startAndRunReview(fx, null);

        LegalReview review = latestReview(fx);
        assertThat(review.getMode()).isEqualTo(ReviewMode.FULL);

        ReviewCycle cycle = activeCycle(fx);
        assertThat(cycle.getStatus()).isEqualTo(ReviewCycleStatus.NEEDS_ACTION);

        List<RevisionRequest> requests =
            revisionRequestRepository.findByReviewCycleIdAndDeletedAtIsNullOrderById(cycle.getId());
        assertThat(requests).hasSize(1);
        RevisionRequest request = requests.get(0);
        assertThat(request.getStatus()).isEqualTo(RevisionRequestStatus.OPEN);
        assertThat(request.getAnchorQuote()).contains("악취 30%");
        assertThat(request.getAnchorSectionCode()).isEqualTo(BusinessPlanSectionCode.PRODUCT_SERVICE);
        assertThat(suggestionRepository
            .findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(request.getId()))
            .hasSizeBetween(2, 3);

        List<LegalReviewQuestion> openQuestions = openQuestions(review.getId());
        assertThat(openQuestions).hasSize(1);
        assertThat(openQuestions.get(0).getCategoriesJson()).contains("INDUSTRY_SPECIFIC");

        assertThat(nowTodos(review.getId())).hasSize(5);
    }

    // ── 시나리오 2: 수정 승인 → 증분 재검토 ──────────────────────────────

    @Test
    void scenario2_acceptCreatesV2AndIncrementalRerunResolvesAdFinding() throws Exception {
        Fixture fx = confirmedFixture();
        startAndRunReview(fx, null);
        StructuredPlan v1 = currentPlan(fx);
        List<String> v1SectionTexts = sectionTexts(v1.getId());
        RevisionRequest request = onlyRevisionRequest(fx);
        RevisionSuggestion suggestionA = suggestionRepository
            .findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(request.getId())
            .stream().filter(s -> s.getLabel().equals("A")).findFirst().orElseThrow();

        mockMvc.perform(post("/api/v1/projects/{pid}/revision-requests/{id}/accept",
                fx.projectId(), request.getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"suggestionId\":%d}".formatted(suggestionA.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.newVersionNumber").value(2))
            .andExpect(jsonPath("$.data.origin").value("REVISION_ACCEPT"));

        StructuredPlan v2 = currentPlan(fx);
        assertThat(v2.getVersionNumber()).isEqualTo(2);
        assertThat(v2.getOrigin()).isEqualTo(PlanOrigin.REVISION_ACCEPT);
        assertThat(v2.getParentPlan().getId()).isEqualTo(v1.getId());
        assertThat(v2.getStatus()).isEqualTo(StructuredPlanStatus.CONFIRMED);
        // v1은 바이트 단위로 불변이어야 한다
        assertThat(sectionTexts(v1.getId())).isEqualTo(v1SectionTexts);
        // v2에는 수정안 A가 반영되고 원 문구는 사라진다
        assertThat(sectionTexts(v2.getId()).stream().anyMatch(t -> t != null && t.contains("악취 30%")))
            .isFalse();
        assertThat(sectionTexts(v2.getId()).stream()
            .anyMatch(t -> t != null && t.contains("공인기관 실증 시험"))).isTrue();
        // 자동 재검토 금지 — 승인만으로는 새 검토 job이 생기지 않는다
        assertThat(jobRepository.existsByProjectIdAndJobTypeAndStatusInAndDeletedAtIsNull(
            fx.projectId(), JobType.LEGAL_REVIEW, List.of(JobStatus.QUEUED, JobStatus.RUNNING)))
            .isFalse();
        assertThat(mockClient.invocations()).hasSize(1);

        startAndRunReview(fx, "{\"mode\":\"INCREMENTAL\"}");

        LegalReview review2 = latestReview(fx);
        assertThat(review2.getStructuredPlan().getId()).isEqualTo(v2.getId());
        assertThat(review2.getMode()).isEqualTo(ReviewMode.INCREMENTAL);
        assertThat(review2.getRerunCategoriesJson()).contains("ADVERTISING_AND_MARKETING");
        assertThat(review2.getCarriedCategoriesJson()).contains("TAX_AND_FINANCIAL");

        // Mock 2차 호출이 증분 파라미터를 받았는지 — 승계 범주 미재실행의 증거
        List<LegalReviewAiRequest> invocations = mockClient.invocations();
        assertThat(invocations).hasSize(2);
        assertThat(invocations.get(1).mode()).isEqualTo(ReviewMode.INCREMENTAL);
        assertThat(invocations.get(1).rerunCategories())
            .contains(LegalCategory.ADVERTISING_AND_MARKETING)
            .doesNotContain(LegalCategory.TAX_AND_FINANCIAL);

        List<LegalFinding> findings =
            findingRepository.findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(review2.getId());
        assertThat(findings).hasSize(10);
        assertThat(findings.stream()
            .filter(f -> f.getCategory() == LegalCategory.ADVERTISING_AND_MARKETING)
            .findFirst().orElseThrow().getCarried()).isFalse();
        assertThat(findings.stream().filter(LegalFinding::getCarried)).hasSize(9);

        // 수정 요청은 삭제되지 않고 v2에서 해결로 기록된다
        RevisionRequest resolved = revisionRequestRepository
            .findByIdAndDeletedAtIsNull(request.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(RevisionRequestStatus.ACCEPTED);
        assertThat(resolved.getResolvedInVersion()).isEqualTo(2);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/projects/{pid}/legal-reviews/latest", fx.projectId())
                .header("X-User-Id", fx.ownerId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.diff.resolved").value(1))
            .andExpect(jsonPath("$.data.diff.added").value(0))
            .andExpect(jsonPath("$.data.diff.maintained").value(5));

        // 질문이 남아 있으므로 여전히 NEEDS_ACTION
        assertThat(activeCycle(fx).getStatus()).isEqualTo(ReviewCycleStatus.NEEDS_ACTION);
        assertThat(openQuestions(review2.getId())).hasSize(1);
    }

    // ── 시나리오 3: 질문 답변 → 수렴 ─────────────────────────────────────

    @Test
    void scenario3_answerCreatesV3WithConfirmedFactAndConverges() throws Exception {
        Fixture fx = confirmedFixture();
        runScenario2Flow(fx);

        LegalReview review2 = latestReview(fx);
        LegalReviewQuestion question = openQuestions(review2.getId()).get(0);

        mockMvc.perform(post("/api/v1/projects/{pid}/legal-questions/{id}/answer",
                fx.projectId(), question.getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"answer":"비대상 (환경산업기술원 확인)",
                     "factKey":"활성탄필터.안전확인대상",
                     "source":"환경산업기술원"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.newVersionNumber").value(3));

        StructuredPlan v3 = currentPlan(fx);
        assertThat(v3.getVersionNumber()).isEqualTo(3);
        assertThat(v3.getOrigin()).isEqualTo(PlanOrigin.ANSWER);
        // 답변은 confirmedFacts로만 저장되고 본문에는 삽입되지 않는다
        assertThat(factRepository.findByReviewCycleIdAndDeletedAtIsNullOrderByAnsweredAt(
            activeCycle(fx).getId())).hasSize(1);
        assertThat(sectionTexts(v3.getId()).stream()
            .anyMatch(t -> t != null && t.contains("비대상"))).isFalse();

        startAndRunReview(fx, "{\"mode\":\"INCREMENTAL\"}");

        // Mock 3차 호출에 확정 정보가 주입됐는지
        List<LegalReviewAiRequest> invocations = mockClient.invocations();
        LegalReviewAiRequest last = invocations.get(invocations.size() - 1);
        assertThat(last.confirmedFacts())
            .anyMatch(fact -> fact.key().contains("활성탄") && fact.value().contains("비대상"));

        LegalReview review3 = latestReview(fx);
        assertThat(openQuestions(review3.getId())).isEmpty();
        assertThat(activeCycle(fx).getStatus()).isEqualTo(ReviewCycleStatus.CONVERGED);
    }

    // ── 시나리오 4: 발행 ─────────────────────────────────────────────────

    @Test
    void scenario4_publishWithIncompleteTodosSucceeds() throws Exception {
        Fixture fx = confirmedFixture();
        runScenario3Flow(fx);

        assertThat(projectStage(fx.projectId())).isEqualTo("LEGAL_REVIEW");
        ReviewCycle cycle = activeCycle(fx);

        mockMvc.perform(post("/api/v1/projects/{pid}/review-cycles/{cid}/publish",
                fx.projectId(), cycle.getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"completedActions":[
                      "통신판매업 신고",
                      "KC 안전확인 대상 여부 확인",
                      "개인정보 처리방침 수립"]}
                    """))
            .andExpect(status().isOk());

        Publication publication = publicationRepository
            .findByReviewCycleIdAndDeletedAtIsNull(cycle.getId()).orElseThrow();
        assertThat(publication.getFinalVersionNumber()).isEqualTo(3);

        JsonNode snapshot = objectMapper.readTree(publication.getSnapshotJson());
        assertThat(snapshot.get("finalVersionNumber").asInt()).isEqualTo(3);
        assertThat(snapshot.get("versions")).hasSize(3);
        // 해결 이력: 광고 수정요청이 v2에서 해결됐다는 기록
        List<Integer> resolvedVersions = new ArrayList<>();
        snapshot.get("resolutions").forEach(node ->
            resolvedVersions.add(node.get("resolvedInVersion").asInt()));
        assertThat(resolvedVersions).contains(2);
        // 미완료 할 일 2건은 이행 예정으로 수록되고 발행을 막지 않는다
        List<String> pending = new ArrayList<>();
        snapshot.get("pendingTodos").forEach(node -> pending.add(node.asText()));
        assertThat(pending).containsExactlyInAnyOrder(
            "청약철회·환불 규정 정비", "사업자등록·부가세 신고 체계 확인");

        assertThat(cycleRepository.findById(cycle.getId()).orElseThrow().getStatus())
            .isEqualTo(ReviewCycleStatus.PUBLISHED);
        assertThat(projectStage(fx.projectId())).isEqualTo("FEASIBILITY");
    }

    // ── 시나리오 5: 발행 후 수정 → 새 사이클 ─────────────────────────────

    @Test
    void scenario5_userEditAfterPublishStartsNewCycleAndPreservesPublication() throws Exception {
        Fixture fx = confirmedFixture();
        runScenario4Flow(fx);

        StructuredPlan v3 = planRepository
            .findTopByProjectIdOrderByVersionNumberDesc(fx.projectId()).orElseThrow();
        Long publishedCycleId = cycleRepository
            .findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
                fx.projectId(), ReviewCycleStatus.DRAFT)
            .map(ReviewCycle::getId).orElseThrow();

        mockMvc.perform(post("/api/v1/projects/{pid}/structured-plans/{planId}/edit",
                fx.projectId(), v3.getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sections":[{"code":"PRODUCT_SERVICE",
                      "sourceText":"필터 성능 표기를 시험 성적서 기반으로 전면 개편한다."}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.newVersionNumber").value(4))
            .andExpect(jsonPath("$.data.origin").value("USER_EDIT"));

        StructuredPlan v4 = planRepository
            .findTopByProjectIdOrderByVersionNumberDesc(fx.projectId()).orElseThrow();
        assertThat(v4.getVersionNumber()).isEqualTo(4);
        assertThat(v4.getOrigin()).isEqualTo(PlanOrigin.USER_EDIT);

        // 새 사이클이 DRAFT로 시작한다
        ReviewCycle newCycle = activeCycle(fx);
        assertThat(newCycle.getStatus()).isEqualTo(ReviewCycleStatus.DRAFT);
        assertThat(newCycle.getCurrentPlan().getId()).isEqualTo(v4.getId());

        // 기존 발행물은 v3 기준 그대로 보존된다
        Publication publication = publicationRepository
            .findByReviewCycleIdAndDeletedAtIsNull(publishedCycleId).orElseThrow();
        assertThat(publication.getFinalPlan().getId()).isEqualTo(v3.getId());
        assertThat(publication.getFinalVersionNumber()).isEqualTo(3);
        assertThat(cycleRepository.findById(publishedCycleId).orElseThrow().getStatus())
            .isEqualTo(ReviewCycleStatus.PUBLISHED);
    }

    // ── 시나리오 플로우 헬퍼 ─────────────────────────────────────────────

    private void runScenario2Flow(Fixture fx) throws Exception {
        startAndRunReview(fx, null);
        RevisionRequest request = onlyRevisionRequest(fx);
        RevisionSuggestion suggestionA = suggestionRepository
            .findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(request.getId())
            .stream().filter(s -> s.getLabel().equals("A")).findFirst().orElseThrow();
        mockMvc.perform(post("/api/v1/projects/{pid}/revision-requests/{id}/accept",
                fx.projectId(), request.getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"suggestionId\":%d}".formatted(suggestionA.getId())))
            .andExpect(status().isOk());
        startAndRunReview(fx, "{\"mode\":\"INCREMENTAL\"}");
    }

    private void runScenario3Flow(Fixture fx) throws Exception {
        runScenario2Flow(fx);
        LegalReviewQuestion question = openQuestions(latestReview(fx).getId()).get(0);
        mockMvc.perform(post("/api/v1/projects/{pid}/legal-questions/{id}/answer",
                fx.projectId(), question.getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"answer":"비대상 (환경산업기술원 확인)",
                     "factKey":"활성탄필터.안전확인대상",
                     "source":"환경산업기술원"}
                    """))
            .andExpect(status().isOk());
        startAndRunReview(fx, "{\"mode\":\"INCREMENTAL\"}");
    }

    private void runScenario4Flow(Fixture fx) throws Exception {
        runScenario3Flow(fx);
        mockMvc.perform(post("/api/v1/projects/{pid}/review-cycles/{cid}/publish",
                fx.projectId(), activeCycle(fx).getId())
                .header("X-User-Id", fx.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"completedActions":[
                      "통신판매업 신고",
                      "KC 안전확인 대상 여부 확인",
                      "개인정보 처리방침 수립"]}
                    """))
            .andExpect(status().isOk());
    }

    // ── 인프라 헬퍼 ─────────────────────────────────────────────────────

    private Fixture confirmedFixture() throws Exception {
        User owner = userRepository.saveAndFlush(User.create(
            UUID.randomUUID() + "@example.com", "hashed", "owner"));
        Project project = projectRepository.saveAndFlush(
            Project.create(owner, "프레시락 미니", null, "AI"));
        var upload = documentCommandService.upload(new DocumentUploadCommand(
            project.getId(), owner.getId(), DocumentType.BUSINESS_PLAN,
            "fresh-lock.docx", BusinessPlanDocxPolicy.DOCX_MIME,
            FRESH_LOCK_DOCX.length, () -> new ByteArrayInputStream(FRESH_LOCK_DOCX),
            UUID.randomUUID().toString()));
        parseExecutor.execute(claimService.claimOne(upload.jobId()).orElseThrow());

        StructuredPlan plan = planRepository
            .findTopByProjectIdOrderByVersionNumberDesc(project.getId()).orElseThrow();
        mockMvc.perform(post("/api/v1/projects/{pid}/structured-plans/{planId}/confirm",
                project.getId(), plan.getId())
                .header("X-User-Id", owner.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d}".formatted(plan.getVersion())))
            .andExpect(status().isOk());
        assertThat(projectStage(project.getId())).isEqualTo("LEGAL_REVIEW");
        return new Fixture(owner.getId(), project.getId());
    }

    private void startAndRunReview(Fixture fx, String body) throws Exception {
        var requestBuilder = post("/api/v1/projects/{pid}/legal-reviews", fx.projectId())
            .header("X-User-Id", fx.ownerId());
        if (body != null) {
            requestBuilder = requestBuilder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mockMvc.perform(requestBuilder)
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(response).get("data").get("jobId").asLong();
        legalExecutor.execute(claimService.claimOne(jobId).orElseThrow());
    }

    private LegalReview latestReview(Fixture fx) {
        return reviewRepository
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                fx.projectId(), fx.ownerId()).orElseThrow();
    }

    private ReviewCycle activeCycle(Fixture fx) {
        return cycleRepository.findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
            fx.projectId(), ReviewCycleStatus.PUBLISHED).orElseThrow();
    }

    private StructuredPlan currentPlan(Fixture fx) {
        return activeCycle(fx).getCurrentPlan();
    }

    private RevisionRequest onlyRevisionRequest(Fixture fx) {
        List<RevisionRequest> requests = revisionRequestRepository
            .findByReviewCycleIdAndDeletedAtIsNullOrderById(activeCycle(fx).getId());
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private List<LegalReviewQuestion> openQuestions(Long reviewId) {
        return questionRepository
            .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(reviewId).stream()
            .filter(question -> question.getStatus() == LegalQuestionStatus.OPEN)
            .toList();
    }

    private List<String> sectionTexts(Long planId) {
        return sectionRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(planId).stream()
            .map(section -> section.getSourceText())
            .toList();
    }

    /** 프론트 collectActions와 동일 규칙: "액션 (시점)" 파싱, 무조치·조건부 제외, 문자열 dedup. */
    private List<String> nowTodos(Long reviewId) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (LegalFinding finding :
            findingRepository.findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(reviewId)) {
            String recommendation = finding.getRecommendation();
            if (recommendation == null) {
                continue;
            }
            for (String part : recommendation.split(" / ")) {
                var matcher = ACTION_PATTERN.matcher(part.strip());
                if (!matcher.matches()) {
                    continue;
                }
                String action = matcher.group(1).strip();
                String timing = matcher.group(2).strip();
                if (!action.isEmpty() && !"계획 실행 시".equals(timing)) {
                    actions.add(action);
                }
            }
        }
        return List.copyOf(actions);
    }

    private String projectStage(Long projectId) {
        return jdbcClient.sql("select stage from projects where id = :id")
            .param("id", projectId).query(String.class).single();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("feedback-loop-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Fixture(Long ownerId, Long projectId) {}

    @TestConfiguration
    static class FeedbackAiConfiguration {
        /** 문서 구조화: 12개 섹션 전부 PRESENT, 프레시락 미니 내용을 결정론적으로 채운다. */
        @Bean
        @Primary
        AiServiceClient freshLockAiServiceClient() {
            return new FreshLockAiServiceClient();
        }

        /** 법률 검토: 시나리오 규칙 Mock. 테스트가 호출 이력을 단언할 수 있게 동일 인스턴스를 노출한다. */
        @Bean
        @Primary
        MockLegalReviewAiClient feedbackMockLegalReviewAiClient() {
            return new MockLegalReviewAiClient();
        }
    }

    static class FreshLockAiServiceClient implements AiServiceClient {
        @Override
        public AiJobAcceptedResponse startJob(AiJobRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiJobStatusResponse getStatus(String externalRequestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(String externalRequestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request) {
            List<AiStructuredPlanItem> items = new ArrayList<>();
            for (DocumentStructureSection section : request.sections()) {
                items.add(new AiStructuredPlanItem(
                    section.code(),
                    section.displayName(),
                    StructuredItemStatus.PRESENT,
                    contentFor(BusinessPlanSectionCode.valueOf(section.code())),
                    "",
                    null,
                    List.of("프레시락 미니 기획서 근거"),
                    List.of(request.blocks().get(0).sequence())
                ));
            }
            return new DocumentStructureAiResponse(
                new AiStructuredPlanResult(
                    "scripted-fresh-lock", "scripted-v1",
                    request.promptVersion(), request.parserVersion(),
                    items, null, List.of()),
                "fresh-lock-" + request.jobId());
        }

        private String contentFor(BusinessPlanSectionCode code) {
            return switch (code) {
                case BUSINESS_OVERVIEW ->
                    "프레시락 미니는 1인 가구용 신선보관 밀폐용기 사업이다.";
                case PRODUCT_SERVICE ->
                    "프레시락 미니는 활성탄 필터를 적용한 신선보관 밀폐용기다. "
                        + AD_SENTENCE + " 필터는 3개월마다 교체하도록 안내한다.";
                case MARKET_SIZE -> "국내 밀폐용기 시장은 연 5% 성장하고 있다.";
                case TARGET_CUSTOMER -> "주 고객은 신선식품을 소량 구매하는 1인 가구다.";
                case COMPETITIVE_ANALYSIS -> "기존 제품 대비 필터 교체형 구조가 차별점이다.";
                case BUSINESS_MODEL -> "자사몰과 오픈마켓을 통한 온라인 직판 모델이다.";
                case COST_PROFITABILITY -> "제조 원가는 판매가의 40% 수준으로 설계한다.";
                case SALES_GOALS_FINANCIAL_PROJECTIONS -> "출시 첫해 10만 개 판매를 목표로 한다.";
                case TECHNOLOGY_PRODUCTION -> "사출 성형과 필터 조립은 국내 협력사에서 진행한다.";
                case LEGAL_PERMITS -> "식품용 기구 표시 기준을 준수할 예정이다.";
                case SCHEDULE_RISK -> "출시는 내년 상반기이며 금형 지연이 주요 리스크다.";
                case EVIDENCE_LIST -> "소비자 조사 결과와 시제품 테스트 결과를 보유하고 있다.";
            };
        }
    }
}
