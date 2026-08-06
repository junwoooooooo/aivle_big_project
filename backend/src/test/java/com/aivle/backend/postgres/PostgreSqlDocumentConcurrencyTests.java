package com.aivle.backend.postgres;

import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.document.application.DocumentCommandService;
import com.aivle.backend.document.application.DocumentUploadCommand;
import com.aivle.backend.file.validation.BusinessPlanDocxPolicy;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import com.aivle.backend.job.runner.JobClaimService;
import com.aivle.backend.job.runner.JobExecutionProperties;
import com.aivle.backend.job.runner.JobRecoveryService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("postgres")
class PostgreSqlDocumentConcurrencyTests extends PostgreSqlIntegrationTestSupport {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final byte[] DOCX_LIKE = {0x50, 0x4b, 0x03, 0x04, 1};

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AnalysisJobRepository jobRepository;
    @Autowired
    private DocumentCommandService commandService;
    @Autowired
    private JobClaimService claimService;
    @Autowired
    private JobRecoveryService recoveryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock jobClock;
    @Autowired
    private JobExecutionProperties jobProperties;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.root", () -> STORAGE_ROOT.toString());
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table users, stored_files restart identity cascade"
        );
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
    void concurrentDifferentUploadsAllocateSequentialVersions() throws Exception {
        Fixture fixture = project();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Long> first = () -> {
                start.await(5, TimeUnit.SECONDS);
                return upload(fixture, "first.docx", "different-1").versionId();
            };
            Callable<Long> second = () -> {
                start.await(5, TimeUnit.SECONDS);
                return upload(fixture, "second.docx", "different-2").versionId();
            };
            var one = executor.submit(first);
            var two = executor.submit(second);
            start.countDown();

            assertThat(java.util.List.of(
                one.get(15, TimeUnit.SECONDS),
                two.get(15, TimeUnit.SECONDS)
            )).doesNotHaveDuplicates();
            assertThat(jdbcTemplate.queryForList("""
                select version_number
                from document_versions
                order by version_number
                """, Integer.class)).containsExactly(1, 2);
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from project_documents where status = 'ACTIVE'",
                Integer.class
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameIdempotencyKeyConvergesToOneVersionAndJob() throws Exception {
        Fixture fixture = project();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<com.aivle.backend.document.application.DocumentUploadResult> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                return upload(fixture, "same.docx", "same-key");
            };
            var one = executor.submit(task);
            var two = executor.submit(task);
            start.countDown();
            var first = one.get(15, TimeUnit.SECONDS);
            var second = two.get(15, TimeUnit.SECONDS);

            assertThat(first.versionId()).isEqualTo(second.versionId());
            assertThat(first.jobId()).isEqualTo(second.jobId());
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from analysis_jobs",
                Integer.class
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameJobClaimHasExactlyOneWinner() throws Exception {
        Long jobId = upload(project(), "claim.docx", "claim-key").jobId();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Optional<JobClaim>> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                return claimService.claimOne(jobId);
            };
            var one = executor.submit(task);
            var two = executor.submit(task);
            start.countDown();
            long winners = java.util.stream.Stream.of(
                one.get(15, TimeUnit.SECONDS),
                two.get(15, TimeUnit.SECONDS)
            ).filter(Optional::isPresent).count();

            assertThat(winners).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleRunningJobIsRecoveredWithoutCreatingDuplicateResult() {
        Long jobId = upload(project(), "stale.docx", "stale-key").jobId();
        claimService.claimOne(jobId).orElseThrow();
        jdbcTemplate.update("""
            update analysis_jobs
            set claimed_at = current_timestamp - interval '10 minutes',
                heartbeat_at = current_timestamp - interval '10 minutes'
            where id = ?
            """,
            jobId
        );
        assertThat(jdbcTemplate.queryForObject("""
            select status = 'RUNNING'
               and coalesce(heartbeat_at, claimed_at)
                   < (current_timestamp - interval '5 minutes')
            from analysis_jobs
            where id = ?
            """, Boolean.class, jobId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
            select coalesce(heartbeat_at, claimed_at) < ?
            from analysis_jobs
            where id = ?
            """, Boolean.class,
            LocalDateTime.now(jobClock).minusMinutes(5),
            jobId
        )).isTrue();
        int repositoryCandidates = transactionTemplate.execute(status -> jobRepository.findRecoveryCandidates(
            com.aivle.backend.common.entity.JobType.DOCUMENT_PARSE,
            com.aivle.backend.common.entity.JobStatus.RUNNING,
            PageRequest.of(0, jobProperties.batchSize())
        ).size());
        assertThat(repositoryCandidates).isEqualTo(1);

        assertThat(recoveryService.recoverStaleJobs()).isEqualTo(1);
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus().name())
            .isEqualTo("QUEUED");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from structured_plans",
            Integer.class
        )).isZero();
    }

    private com.aivle.backend.document.application.DocumentUploadResult upload(
        Fixture fixture,
        String fileName,
        String idempotencyKey
    ) {
        return commandService.upload(new DocumentUploadCommand(
            fixture.project().getId(),
            fixture.owner().getId(),
            DocumentType.BUSINESS_PLAN,
            fileName,
            BusinessPlanDocxPolicy.DOCX_MIME,
            DOCX_LIKE.length,
            () -> new ByteArrayInputStream(DOCX_LIKE),
            idempotencyKey
        ));
    }

    private Fixture project() {
        User owner = userRepository.saveAndFlush(User.create(
            UUID.randomUUID() + "@example.com",
            "hashed",
            "owner"
        ));
        Project project = projectRepository.saveAndFlush(
            Project.create(owner, "idea", null, "AI")
        );
        return new Fixture(owner, project);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("phase2-postgres-files-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Fixture(User owner, Project project) {
    }
}
