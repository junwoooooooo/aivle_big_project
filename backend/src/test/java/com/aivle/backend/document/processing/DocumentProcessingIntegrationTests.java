package com.aivle.backend.document.processing;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.TestDocxFactory;
import com.aivle.backend.document.application.DocumentCommandService;
import com.aivle.backend.document.application.DocumentUploadCommand;
import com.aivle.backend.document.application.processing.DocumentParseJobExecutor;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.document.repository.*;
import com.aivle.backend.document.structure.*;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.validation.BusinessPlanDocxPolicy;
import com.aivle.backend.integration.ai.AiServiceClient;
import com.aivle.backend.integration.ai.document.*;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DocumentProcessingIntegrationTests.ScriptedAiConfiguration.class)
class DocumentProcessingIntegrationTests {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final byte[] VALID_DOCX = TestDocxFactory.document(
        "사업 개요와 제품 서비스",
        "시장 규모, 타겟 고객, 경쟁 분석",
        "비즈니스 모델, 원가 수익성, 판매 목표와 재무 추정",
        "기술 생산, 법률 인허가, 일정 리스크, 근거 자료 목록"
    );

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired DocumentCommandService commandService;
    @Autowired JobClaimService claimService;
    @Autowired DocumentParseJobExecutor executor;
    @Autowired JobFailureService failureService;
    @Autowired StructuredPlanRepository planRepository;
    @Autowired StructuredPlanSectionRepository sectionRepository;
    @Autowired MissingFieldRepository missingFieldRepository;
    @Autowired FileStorage fileStorage;
    @Autowired ObjectStoragePort objectStorage;
    @Autowired JdbcClient jdbcClient;
    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:h2:mem:phase1c-processing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("app.file-storage.root", () -> STORAGE_ROOT.toString());
        registry.add(
            "app.object-storage.local-root",
            () -> STORAGE_ROOT.toString()
        );
    }

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of(
            "audit_events",
            "refresh_tokens",
            "missing_fields",
            "structured_plan_sections",
            "structured_plans",
            "analysis_jobs",
            "document_versions",
            "project_documents",
            "stored_files",
            "projects",
            "users"
        )) {
            jdbcClient.sql("delete from " + table).update();
        }
    }

    @AfterAll
    static void cleanupStorage() throws IOException {
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void fullPipelinePersistsTwelveSectionsAndSucceeds() {
        UploadFixture fixture = uploaded("all-present.docx", VALID_DOCX);
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        executor.execute(claim);

        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        StructuredPlan plan = planRepository.findBySourceDocumentVersionIdAndDeletedAtIsNull(
            fixture.versionId()
        ).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getResultReferenceType()).isEqualTo("STRUCTURED_PLAN");
        assertThat(job.getResultReferenceId()).isEqualTo(plan.getId());
        assertThat(job.getExternalRequestId()).startsWith("scripted-");
        assertThat(versionStatus(fixture.jobId())).isEqualTo("SUCCEEDED");
        assertThat(plan.getStatus()).isEqualTo(StructuredPlanStatus.DRAFT);
        assertThat(plan.getCompletionRate()).isEqualTo(100);
        assertThat(plan.getProvider()).isEqualTo("scripted-test");
        assertThat(plan.getRawResultHash()).hasSize(64);
        assertThat(plan.getConfirmedByUser()).isFalse();
        assertThat(sectionRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(plan.getId()))
            .hasSize(12)
            .extracting(section -> section.getSequence())
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId()))
            .isEmpty();
        assertThat(parseMetadata(fixture.jobId()))
            .contains("parserName")
            .contains("totalCharacters")
            .doesNotContain(fixture.storageKey());
        ParserArtifactRow artifact = parserArtifact(
            fixture.versionId()
        );
        assertThat(artifact.status()).isEqualTo("SUCCEEDED");
        assertThat(artifact.schemaVersion())
            .isEqualTo("document-blocks-v1");
        assertThat(artifact.parserVersion())
            .isEqualTo("spring-docx-blocks-v2");
        assertThat(artifact.blockCount()).isPositive();
        assertThat(artifact.checksum()).matches("[0-9a-f]{64}");
        assertThat(artifact.storageKey())
            .contains("/parser/spring-docx-blocks-v2/")
            .endsWith(artifact.checksum() + ".json");
        assertThat(objectStorage.exists(artifact.storageKey()))
            .isTrue();
        assertThat(projectStage(fixture.projectId())).isEqualTo("STRUCTURING");
    }

    @Test
    void missingSectionProducesNeedsInputAndPartialStates() {
        UploadFixture fixture = uploaded("partial.docx", VALID_DOCX);
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        executor.execute(claim);

        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        StructuredPlan plan = planRepository.findBySourceDocumentVersionIdAndDeletedAtIsNull(
            fixture.versionId()
        ).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PARTIAL);
        assertThat(versionStatus(fixture.jobId())).isEqualTo("PARTIAL");
        assertThat(plan.getStatus()).isEqualTo(StructuredPlanStatus.NEEDS_INPUT);
        assertThat(plan.getCompletionRate()).isEqualTo(91);
        assertThat(missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId()))
            .singleElement()
            .satisfies(field -> {
                assertThat(field.getStatus()).isEqualTo(MissingFieldStatus.OPEN);
                assertThat(field.getSectionCode())
                    .isEqualTo(BusinessPlanSectionCode.BUSINESS_OVERVIEW);
                assertThat(field.getPriority()).isEqualTo(Priority.HIGH);
            });
    }

    @Test
    void parserFailureStoresNoResultAndFailsNonRetryably() {
        UploadFixture fixture = uploaded(
            "broken.docx",
            new byte[] {0x50, 0x4b, 0x03, 0x04, 1}
        );
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        JobProcessingException failure = catchThrowableOfType(
            () -> executor.execute(claim),
            JobProcessingException.class
        );
        failureService.handle(claim, failure);

        assertThat(jobStatus(fixture.jobId())).isEqualTo("FAILED");
        assertThat(versionStatus(fixture.jobId())).isEqualTo("FAILED");
        assertThat(planRepository.existsBySourceDocumentVersionIdAndDeletedAtIsNull(
            fixture.versionId()
        )).isFalse();
    }

    @Test
    void retryableProviderFailureSchedulesSameJobWithoutResult() {
        UploadFixture fixture = uploaded("retry.docx", VALID_DOCX);
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        JobProcessingException failure = catchThrowableOfType(
            () -> executor.execute(claim),
            JobProcessingException.class
        );
        failureService.handle(claim, failure);

        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isNotNull();
        assertThat(job.getLastErrorCode()).isEqualTo("AI_HTTP_503");
        assertThat(versionStatus(fixture.jobId())).isEqualTo("QUEUED");
        assertThat(planRepository.existsBySourceDocumentVersionIdAndDeletedAtIsNull(
            fixture.versionId()
        )).isFalse();
    }

    @Test
    void invalidSourceReferenceFailsMappingWithoutPersistence() {
        UploadFixture fixture = uploaded("invalid-reference.docx", VALID_DOCX);
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        JobProcessingException failure = catchThrowableOfType(
            () -> executor.execute(claim),
            JobProcessingException.class
        );
        failureService.handle(claim, failure);

        assertThat(jobStatus(fixture.jobId())).isEqualTo("FAILED");
        assertThat(planRepository.existsBySourceDocumentVersionIdAndDeletedAtIsNull(
            fixture.versionId()
        )).isFalse();
    }

    @Test
    void missingStoredFileFailsWithoutRetry() throws Exception {
        UploadFixture fixture = uploaded("missing-file.docx", VALID_DOCX);
        objectStorage.delete(fixture.storageKey());
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        JobProcessingException failure = catchThrowableOfType(
            () -> executor.execute(claim),
            JobProcessingException.class
        );
        failureService.handle(claim, failure);

        assertThat(failure.isRetryable()).isFalse();
        assertThat(failure.getErrorCode()).isEqualTo("STORED_FILE_MISSING");
        assertThat(jobStatus(fixture.jobId())).isEqualTo("FAILED");
    }

    @Test
    void oldAttemptCannotPersistAfterRetryWasScheduled() {
        UploadFixture fixture = uploaded("retry.docx", VALID_DOCX);
        JobClaim oldClaim = claimService.claimOne(fixture.jobId()).orElseThrow();
        failureService.handle(oldClaim, JobProcessingException.retryable(
            "AI_HTTP_503",
            "AI 서비스가 일시적으로 응답하지 않습니다.",
            Duration.ZERO,
            null
        ));

        assertThatThrownBy(() -> executor.execute(oldClaim))
            .isInstanceOf(JobProcessingException.class)
            .extracting("errorCode")
            .isEqualTo("JOB_CLAIM_LOST");
        assertThat(planRepository.existsBySourceDocumentVersionIdAndDeletedAtIsNull(
            fixture.versionId()
        )).isFalse();
    }

    @Test
    void completedJobCannotCreateDuplicateResult() {
        UploadFixture fixture = uploaded("all-present.docx", VALID_DOCX);
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();
        executor.execute(claim);

        assertThatThrownBy(() -> executor.execute(claim))
            .isInstanceOf(JobProcessingException.class);
        assertThat(jdbcClient.sql("""
            select count(*) from structured_plans where source_document_version_id = :id
            """).param("id", fixture.versionId()).query(Long.class).single())
            .isEqualTo(1);
    }

    @Test
    void latestStructuredPlanApiReturnsSectionsAndNoInternalStorageData() throws Exception {
        UploadFixture fixture = uploaded("partial.docx", VALID_DOCX);
        executor.execute(claimService.claimOne(fixture.jobId()).orElseThrow());

        mockMvc.perform(get("/api/v1/projects/{projectId}/structured-plans/latest",
                fixture.projectId())
                .header("X-User-Id", fixture.ownerId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("NEEDS_INPUT"))
            .andExpect(jsonPath("$.data.sections.length()").value(12))
            .andExpect(jsonPath("$.data.sections[0].sectionCode")
                .value("BUSINESS_OVERVIEW"))
            .andExpect(jsonPath("$.data.missingFields.length()").value(1))
            .andExpect(jsonPath("$.data.rawResultHash").doesNotExist())
            .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    void latestStructuredPlanIs404ForAnotherUserAndWhenAbsent() throws Exception {
        UploadFixture fixture = uploaded("all-present.docx", VALID_DOCX);
        User other = userRepository.saveAndFlush(User.create(
            UUID.randomUUID() + "@example.com", "hashed", "other"
        ));

        mockMvc.perform(get("/api/v1/projects/{projectId}/structured-plans/latest",
                fixture.projectId())
                .header("X-User-Id", fixture.ownerId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("STRUCTURED_PLAN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/structured-plans/latest",
                fixture.projectId())
                .header("X-User-Id", other.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void fillingLastFieldRecalculatesCompletionThenConfirmAdvancesProject()
        throws Exception {
        UploadFixture fixture = uploaded("partial.docx", VALID_DOCX);
        executor.execute(claimService.claimOne(fixture.jobId()).orElseThrow());
        StructuredPlan plan = planRepository
            .findBySourceDocumentVersionIdAndDeletedAtIsNull(fixture.versionId())
            .orElseThrow();
        var field = missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId())
            .get(0);
        String userValue = "user supplied business overview";

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .header("X-Request-Id", "fill-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status":"FILLED",
                      "value":"%s",
                      "version":%d
                    }
                    """.formatted(userValue, field.getVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FILLED"))
            .andExpect(jsonPath("$.data.userValue").value(userValue));

        StructuredPlan completed = planRepository.findById(plan.getId())
            .orElseThrow();
        assertThat(completed.getCompletionRate()).isEqualTo(100);
        assertThat(completed.getStatus()).isEqualTo(StructuredPlanStatus.DRAFT);
        assertThat(jdbcClient.sql("""
            select metadata_json from audit_events
            where event_type = 'MISSING_FIELD_FILLED'
            """).query(String.class).single())
            .contains("BUSINESS_OVERVIEW")
            .doesNotContain(userValue);

        mockMvc.perform(post(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/confirm",
                fixture.projectId(),
                plan.getId())
                .header("X-User-Id", fixture.ownerId())
                .header("X-Request-Id", "confirm-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":%d}
                    """.formatted(completed.getVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.confirmedBy")
                .value(fixture.ownerId()));

        StructuredPlan confirmed = planRepository.findById(plan.getId())
            .orElseThrow();
        assertThat(confirmed.getConfirmedByUser()).isTrue();
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getConfirmedBy().getId())
            .isEqualTo(fixture.ownerId());
        assertThat(projectStage(fixture.projectId()))
            .isEqualTo("LEGAL_REVIEW");
        assertThat(jdbcClient.sql("""
            select count(*) from audit_events
            where event_type = 'STRUCTURED_PLAN_CONFIRMED'
              and request_id = 'confirm-request'
            """).query(Integer.class).single()).isEqualTo(1);

        mockMvc.perform(post(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/confirm",
                fixture.projectId(),
                plan.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":%d}
                    """.formatted(confirmed.getVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        mockMvc.perform(post(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/confirm",
                fixture.projectId(),
                plan.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":%d}
                    """.formatted(completed.getVersion())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code")
                .value("RESOURCE_VERSION_CONFLICT"));

        var resolved = missingFieldRepository.findById(field.getId())
            .orElseThrow();
        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"FILLED","value":"new value","version":%d}
                    """.formatted(resolved.getVersion())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PLAN_NOT_EDITABLE"));
    }

    @Test
    void waiverRequiresReasonAndAlsoCompletesLinkedSection() throws Exception {
        UploadFixture fixture = uploaded("partial.docx", VALID_DOCX);
        executor.execute(claimService.claimOne(fixture.jobId()).orElseThrow());
        StructuredPlan plan = planRepository
            .findBySourceDocumentVersionIdAndDeletedAtIsNull(fixture.versionId())
            .orElseThrow();
        var field = missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId())
            .get(0);

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"FILLED","version":%d}
                    """.formatted(field.getVersion())))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status":"FILLED",
                      "value":"bad\\u0001value",
                      "version":%d
                    }
                    """.formatted(field.getVersion())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"WAIVED","version":%d}
                    """.formatted(field.getVersion())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status":"WAIVED",
                      "reason":"not available at this stage",
                      "version":%d
                    }
                    """.formatted(field.getVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAIVED"));
        assertThat(planRepository.findById(plan.getId()).orElseThrow()
            .getCompletionRate()).isEqualTo(100);
    }

    @Test
    void mutationAndConfirmationEnforceOwnershipVersionAndCompleteness()
        throws Exception {
        UploadFixture fixture = uploaded("partial.docx", VALID_DOCX);
        executor.execute(claimService.claimOne(fixture.jobId()).orElseThrow());
        StructuredPlan plan = planRepository
            .findBySourceDocumentVersionIdAndDeletedAtIsNull(fixture.versionId())
            .orElseThrow();
        var field = missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId())
            .get(0);
        User other = userRepository.saveAndFlush(User.create(
            UUID.randomUUID() + "@example.com",
            "hashed",
            "other"
        ));
        UploadFixture second = uploaded("partial-second.docx", VALID_DOCX);
        executor.execute(claimService.claimOne(second.jobId()).orElseThrow());
        StructuredPlan secondPlan = planRepository
            .findBySourceDocumentVersionIdAndDeletedAtIsNull(second.versionId())
            .orElseThrow();
        var otherPlanField = missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(
                secondPlan.getId()
            ).get(0);

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", other.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"FILLED","value":"value","version":%d}
                    """.formatted(field.getVersion())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                otherPlanField.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"FILLED","value":"value","version":%d}
                    """.formatted(otherPlanField.getVersion())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code")
                .value("MISSING_FIELD_NOT_FOUND"));

        mockMvc.perform(patch(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/missing-fields/{fieldId}",
                fixture.projectId(),
                plan.getId(),
                field.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"FILLED","value":"value","version":999}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code")
                .value("RESOURCE_VERSION_CONFLICT"));

        mockMvc.perform(post(
                "/api/v1/projects/{projectId}/structured-plans/{planId}/confirm",
                fixture.projectId(),
                plan.getId())
                .header("X-User-Id", fixture.ownerId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":%d}
                    """.formatted(plan.getVersion())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("PLAN_INCOMPLETE"));
    }

    private UploadFixture uploaded(String fileName, byte[] content) {
        User owner = userRepository.saveAndFlush(User.create(
            UUID.randomUUID() + "@example.com", "hashed", "owner"
        ));
        Project project = projectRepository.saveAndFlush(
            Project.create(owner, "idea", null, "AI")
        );
        var result = commandService.upload(new DocumentUploadCommand(
            project.getId(),
            owner.getId(),
            DocumentType.BUSINESS_PLAN,
            fileName,
            BusinessPlanDocxPolicy.DOCX_MIME,
            content.length,
            () -> new ByteArrayInputStream(content),
            UUID.randomUUID().toString()
        ));
        String storageKey = jdbcClient.sql("""
            select f.storage_key
            from analysis_jobs j
            join document_versions v on v.id = j.source_document_version_id
            join stored_files f on f.id = v.stored_file_id
            where j.id = :id
            """)
            .param("id", result.jobId())
            .query(String.class)
            .single();
        return new UploadFixture(
            owner.getId(),
            project.getId(),
            result.jobId(),
            result.versionId(),
            storageKey
        );
    }

    private String jobStatus(Long jobId) {
        return jdbcClient.sql("select status from analysis_jobs where id = :id")
            .param("id", jobId).query(String.class).single();
    }

    private String versionStatus(Long jobId) {
        return jdbcClient.sql("""
            select v.parse_status from analysis_jobs j
            join document_versions v on v.id = j.source_document_version_id
            where j.id = :id
            """).param("id", jobId).query(String.class).single();
    }

    private String parseMetadata(Long jobId) {
        return jdbcClient.sql("""
            select v.parse_metadata_json from analysis_jobs j
            join document_versions v on v.id = j.source_document_version_id
            where j.id = :id
            """).param("id", jobId).query(String.class).single();
    }

    private ParserArtifactRow parserArtifact(Long versionId) {
        return jdbcClient.sql("""
            select dv.parser_artifact_status,
                   dv.parser_artifact_schema_version,
                   dv.parser_version,
                   dv.parser_block_count,
                   dv.parser_artifact_checksum_sha256,
                   sf.storage_key
            from document_versions dv
            join stored_files sf
              on sf.id = dv.parser_artifact_stored_file_id
            where dv.id = :versionId
            """)
            .param("versionId", versionId)
            .query((resultSet, rowNumber) ->
                new ParserArtifactRow(
                    resultSet.getString(
                        "parser_artifact_status"
                    ),
                    resultSet.getString(
                        "parser_artifact_schema_version"
                    ),
                    resultSet.getString("parser_version"),
                    resultSet.getInt("parser_block_count"),
                    resultSet.getString(
                        "parser_artifact_checksum_sha256"
                    ),
                    resultSet.getString("storage_key")
                )
            )
            .single();
    }

    private record ParserArtifactRow(
        String status,
        String schemaVersion,
        String parserVersion,
        int blockCount,
        String checksum,
        String storageKey
    ) {
    }

    private String projectStage(Long projectId) {
        return jdbcClient.sql("select stage from projects where id = :id")
            .param("id", projectId).query(String.class).single();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("phase1c-processing-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record UploadFixture(
        Long ownerId,
        Long projectId,
        Long jobId,
        Long versionId,
        String storageKey
    ) {
    }

    @TestConfiguration
    static class ScriptedAiConfiguration {
        @Bean
        @Primary
        AiServiceClient scriptedAiServiceClient() {
            return new ScriptedAiServiceClient();
        }
    }

    static class ScriptedAiServiceClient implements AiServiceClient {
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
        public DocumentStructureAiResponse structureDocument(
            DocumentStructureAiRequest request
        ) {
            if (request.originalFileName().startsWith("retry")) {
                throw new AiClientException(
                    "AI_HTTP_503",
                    "AI 서비스가 일시적으로 응답하지 않습니다.",
                    true,
                    Duration.ZERO,
                    null
                );
            }
            List<AiStructuredPlanItem> items = new ArrayList<>();
            for (int index = 0; index < request.sections().size(); index++) {
                DocumentStructureSection section = request.sections().get(index);
                StructuredItemStatus status =
                    request.originalFileName().startsWith("partial") && index == 0
                        ? StructuredItemStatus.MISSING
                        : StructuredItemStatus.PRESENT;
                List<Integer> references =
                    request.originalFileName().startsWith("invalid-reference") && index == 0
                        ? List.of(999999)
                        : List.of(request.blocks().get(0).sequence());
                items.add(new AiStructuredPlanItem(
                    section.code(),
                    section.displayName(),
                    status,
                    status == StructuredItemStatus.PRESENT ? "확인된 내용" : null,
                    status == StructuredItemStatus.PRESENT ? "" : "필수 내용 누락",
                    null,
                    List.of("테스트 근거"),
                    references
                ));
            }
            return new DocumentStructureAiResponse(
                new AiStructuredPlanResult(
                    "scripted-test",
                    "scripted-v1",
                    request.promptVersion(),
                    request.parserVersion(),
                    items,
                    null,
                    List.of()
                ),
                "scripted-" + request.jobId()
            );
        }
    }
}
