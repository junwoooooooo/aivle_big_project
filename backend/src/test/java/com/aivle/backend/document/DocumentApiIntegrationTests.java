package com.aivle.backend.document;

import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.document.application.DocumentCommandService;
import com.aivle.backend.document.application.DocumentUploadCommand;
import com.aivle.backend.document.application.DocumentUploadResult;
import com.aivle.backend.document.repository.DocumentVersionRepository;
import com.aivle.backend.file.validation.BusinessPlanDocxPolicy;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentApiIntegrationTests {
    private static final byte[] DOCX_A = {0x50, 0x4b, 0x03, 0x04, 1};
    private static final byte[] DOCX_B = {0x50, 0x4b, 0x03, 0x04, 2};
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired DocumentCommandService commandService;
    @Autowired JdbcClient jdbcClient;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.root", () -> STORAGE_ROOT.toString());
        registry.add(
            "app.object-storage.local-root",
            () -> STORAGE_ROOT.toString()
        );
    }

    @AfterAll
    static void cleanStorage() throws IOException {
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void multipartUploadReturns202EnvelopeAndQueuedIdentifiers() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", fixture.project().getId())
                .file(file("plan.docx", DOCX_A))
                .header("X-User-Id", fixture.owner().getId())
                .header("Idempotency-Key", "upload-202"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.projectId").value(fixture.project().getId()))
            .andExpect(jsonPath("$.data.documentId").isNumber())
            .andExpect(jsonPath("$.data.versionId").isNumber())
            .andExpect(jsonPath("$.data.jobId").isNumber())
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.storedFileId").doesNotExist());
    }

    @Test
    @Transactional
    void uploadStoresSourceThroughObjectStorageWithOpaqueKey() {
        Fixture fixture = fixture();

        DocumentUploadResult uploaded = commandService.upload(
            command(fixture, DOCX_A, "object-source")
        );
        var version = documentVersionRepository.findById(
            uploaded.versionId()
        ).orElseThrow();
        var stored = version.getStoredFile();

        assertThat(stored.getStorageKey())
            .startsWith(
                "projects/" + fixture.project().getId()
                    + "/documents/" + uploaded.documentId()
                    + "/versions/" + uploaded.versionId()
                    + "/source/"
            )
            .endsWith(".docx")
            .doesNotContain("plan.docx");
        assertThat(stored.getSizeBytes()).isEqualTo(DOCX_A.length);
        assertThat(stored.getChecksumSha256())
            .matches("[0-9a-f]{64}");
        assertThat(Files.isRegularFile(
            STORAGE_ROOT.resolve(stored.getStorageKey())
        )).isTrue();
    }

    @Test
    void sameIdempotencyKeyAndPayloadReturnsSameIdentifiers() {
        Fixture fixture = fixture();
        DocumentUploadResult first = commandService.upload(command(fixture, DOCX_A, "same-key"));
        DocumentUploadResult second = commandService.upload(command(fixture, DOCX_A, " same-key "));

        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(second.versionId()).isEqualTo(first.versionId());
        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.created()).isFalse();
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadReturns409() throws Exception {
        Fixture fixture = fixture();
        commandService.upload(command(fixture, DOCX_A, "conflict-key"));

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", fixture.project().getId())
                .file(file("plan.docx", DOCX_B))
                .header("X-User-Id", fixture.owner().getId())
                .header("Idempotency-Key", "conflict-key"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void listAndVersionResponsesDoNotExposeStoragePathOrChecksum() throws Exception {
        Fixture fixture = fixture();
        DocumentUploadResult uploaded = commandService.upload(command(fixture, DOCX_A, "list-key"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", fixture.project().getId())
                .header("X-User-Id", fixture.owner().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].documentId").value(uploaded.documentId()))
            .andExpect(jsonPath("$.data[0].currentVersion").value(1))
            .andExpect(jsonPath("$.data[0].latestVersionId").value(uploaded.versionId()));

        mockMvc.perform(get("/api/v1/documents/{documentId}/versions/{versionId}",
                uploaded.documentId(), uploaded.versionId())
                .header("X-User-Id", fixture.owner().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionId").value(uploaded.versionId()))
            .andExpect(jsonPath("$.data.originalFileName").value("plan.docx"))
            .andExpect(jsonPath("$.data.sizeBytes").value(DOCX_A.length))
            .andExpect(jsonPath("$.data.parserArtifactStatus").value("QUEUED"))
            .andExpect(jsonPath("$.data.parserVersion").isEmpty())
            .andExpect(jsonPath("$.data.parserArtifactSchemaVersion").isEmpty())
            .andExpect(jsonPath("$.data.parserBlockCount").isEmpty())
            .andExpect(jsonPath("$.data.parsedAt").isEmpty())
            .andExpect(jsonPath("$.data.storageKey").doesNotExist())
            .andExpect(jsonPath("$.data.checksum").doesNotExist());
    }

    @Test
    void anotherUserGets404ForProjectDocumentVersionAndJob() throws Exception {
        Fixture ownerFixture = fixture();
        User other = userRepository.saveAndFlush(User.create(
            uniqueEmail(), "hashed", "other"
        ));
        DocumentUploadResult uploaded = commandService.upload(
            command(ownerFixture, DOCX_A, "private-key")
        );

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", ownerFixture.project().getId())
                .header("X-User-Id", other.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/versions/{versionId}",
                uploaded.documentId(), uploaded.versionId())
                .header("X-User-Id", other.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("DOCUMENT_VERSION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/jobs/{jobId}", uploaded.jobId())
                .header("X-User-Id", other.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/jobs/latest",
                ownerFixture.project().getId())
                .param("jobType", "DOCUMENT_PARSE")
                .header("X-User-Id", other.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void latestProjectJobSupportsRefreshRecoveryWithoutExposingOtherProjects() throws Exception {
        Fixture fixture = fixture();
        DocumentUploadResult first = commandService.upload(
            command(fixture, DOCX_A, "recovery-first")
        );
        DocumentUploadResult latest = commandService.upload(
            command(fixture, DOCX_B, "recovery-latest")
        );

        mockMvc.perform(get("/api/v1/projects/{projectId}/jobs/latest",
                fixture.project().getId())
                .param("jobType", "DOCUMENT_PARSE")
                .header("X-User-Id", fixture.owner().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobId").value(latest.jobId()))
            .andExpect(jsonPath("$.data.jobId").value(org.hamcrest.Matchers.not(first.jobId())))
            .andExpect(jsonPath("$.data.projectId").value(fixture.project().getId()))
            .andExpect(jsonPath("$.data.jobType").value("DOCUMENT_PARSE"))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.sourceDocumentVersionId").value(latest.versionId()));

        Fixture empty = fixture();
        mockMvc.perform(get("/api/v1/projects/{projectId}/jobs/latest",
                empty.project().getId())
                .param("jobType", "DOCUMENT_PARSE")
                .header("X-User-Id", empty.owner().getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/jobs/latest",
                fixture.project().getId())
                .param("jobType", "LEGAL_REVIEW")
                .header("X-User-Id", fixture.owner().getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void pdfAndFakeSignatureAreRejectedWithoutDatabaseRows() throws Exception {
        Fixture fixture = fixture();
        long before = count("stored_files");

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", fixture.project().getId())
                .file(new MockMultipartFile(
                    "file", "plan.pdf", "application/pdf", new byte[] {1, 2, 3}
                ))
                .header("X-User-Id", fixture.owner().getId()))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.error.code").value("FILE_TYPE_UNSUPPORTED"));

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", fixture.project().getId())
                .file(file("fake.docx", new byte[] {1, 2, 3, 4}))
                .header("X-User-Id", fixture.owner().getId()))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.error.code").value("FILE_SIGNATURE_INVALID"));

        assertThat(count("stored_files")).isEqualTo(before);
    }

    @Test
    void sameKeyConcurrentUploadsConvergeToOneVersionAndOneJob() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<DocumentUploadResult> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                return commandService.upload(command(fixture, DOCX_A, "race-same"));
            };
            Future<DocumentUploadResult> first = executor.submit(task);
            Future<DocumentUploadResult> second = executor.submit(task);
            start.countDown();

            DocumentUploadResult a = first.get(15, TimeUnit.SECONDS);
            DocumentUploadResult b = second.get(15, TimeUnit.SECONDS);
            assertThat(a.versionId()).isEqualTo(b.versionId());
            assertThat(a.jobId()).isEqualTo(b.jobId());
            assertThat(countJobs(fixture.project().getId(), "race-same")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentKeyConcurrentUploadsAllocateDistinctSequentialVersions() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DocumentUploadResult> first = executor.submit(
                () -> uploadAfter(start, command(fixture, DOCX_A, "race-a"))
            );
            Future<DocumentUploadResult> second = executor.submit(
                () -> uploadAfter(start, command(fixture, DOCX_B, "race-b"))
            );
            start.countDown();

            DocumentUploadResult a = first.get(15, TimeUnit.SECONDS);
            DocumentUploadResult b = second.get(15, TimeUnit.SECONDS);
            assertThat(a.versionId()).isNotEqualTo(b.versionId());
            List<Integer> versions = documentVersionRepository
                .findAllByDocumentIdOrderByVersionNumberDesc(a.documentId())
                .stream()
                .map(version -> version.getVersionNumber())
                .sorted()
                .toList();
            assertThat(versions).containsExactly(1, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private DocumentUploadResult uploadAfter(
        CountDownLatch start,
        DocumentUploadCommand command
    ) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return commandService.upload(command);
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile(
            "file", name, BusinessPlanDocxPolicy.DOCX_MIME, content
        );
    }

    private DocumentUploadCommand command(Fixture fixture, byte[] content, String key) {
        return new DocumentUploadCommand(
            fixture.project().getId(),
            fixture.owner().getId(),
            DocumentType.BUSINESS_PLAN,
            "plan.docx",
            BusinessPlanDocxPolicy.DOCX_MIME,
            content.length,
            () -> new ByteArrayInputStream(content),
            key
        );
    }

    private Fixture fixture() {
        User owner = userRepository.saveAndFlush(User.create(
            uniqueEmail(), "hashed", "owner"
        ));
        Project project = projectRepository.saveAndFlush(
            Project.create(owner, "idea", null, "AI")
        );
        return new Fixture(owner, project);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private long count(String table) {
        return jdbcClient.sql("select count(*) from " + table).query(Long.class).single();
    }

    private long countJobs(Long projectId, String key) {
        return jdbcClient.sql("""
            select count(*) from analysis_jobs
            where project_id = :projectId and idempotency_key = :key
            """)
            .param("projectId", projectId)
            .param("key", key)
            .query(Long.class)
            .single();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("aivle-phase1b-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Fixture(User owner, Project project) {
    }
}
