package com.aivle.backend.job.runner;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.document.application.DocumentCommandService;
import com.aivle.backend.document.application.DocumentUploadCommand;
import com.aivle.backend.file.validation.BusinessPlanDocxPolicy;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JobClaimIntegrationTests {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final byte[] DOCX_LIKE = {0x50, 0x4b, 0x03, 0x04, 1};

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired DocumentCommandService commandService;
    @Autowired JobClaimService claimService;
    @Autowired JobFailureService failureService;
    @Autowired JobRecoveryService recoveryService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbcClient;
    @Autowired Clock jobClock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:h2:mem:phase1c-claim;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("app.file-storage.root", () -> STORAGE_ROOT.toString());
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @BeforeEach
    void cleanDatabase() {
        for (String table : java.util.List.of(
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

    @Test
    void claimsDueQueuedDocumentJobAndSynchronizesVersion() {
        JobFixture fixture = uploaded("claim.docx");

        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();
        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();

        assertThat(claim.attempt()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getClaimToken()).isEqualTo(claim.claimToken());
        assertThat(job.getClaimedBy()).isNotBlank();
        assertThat(versionStatus(fixture.jobId())).isEqualTo("RUNNING");
    }

    @Test
    void sameJobCannotBeClaimedTwice() {
        JobFixture fixture = uploaded("double.docx");
        assertThat(claimService.claimOne(fixture.jobId())).isPresent();
        assertThat(claimService.claimOne(fixture.jobId())).isEmpty();
    }

    @Test
    void concurrentClaimProducesOneWinner() throws Exception {
        JobFixture fixture = uploaded("concurrent.docx");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Optional<JobClaim>> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                return claimService.claimOne(fixture.jobId());
            };
            Future<Optional<JobClaim>> first = executor.submit(task);
            Future<Optional<JobClaim>> second = executor.submit(task);
            start.countDown();
            long winners = java.util.stream.Stream.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            ).filter(Optional::isPresent).count();
            assertThat(winners).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void claimBatchHonorsConfiguredSize() {
        for (int index = 0; index < 7; index++) {
            uploaded("batch-" + index + ".docx");
        }
        assertThat(claimService.claimBatch()).hasSize(5);
    }

    @Test
    void jobsWithoutSourceVersionAndOtherTypesAreExcluded() {
        Fixture fixture = project();
        jobRepository.saveAndFlush(
            AnalysisJob.queued(fixture.project(), JobType.DOCUMENT_PARSE, "{}")
        );
        jobRepository.saveAndFlush(
            AnalysisJob.queued(fixture.project(), JobType.MARKET_ANALYSIS, "{}")
        );
        assertThat(claimService.claimBatch()).isEmpty();
    }

    @Test
    void futureRetryIsNotClaimed() {
        JobFixture fixture = uploaded("future.docx");
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();
        failureService.handle(claim, JobProcessingException.retryable(
            "AI_HTTP_429",
            "AI 서비스가 일시적으로 응답하지 않습니다.",
            Duration.ofSeconds(60),
            null
        ));

        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getNextAttemptAt()).isAfter(LocalDateTime.now(jobClock));
        assertThat(claimService.claimOne(fixture.jobId())).isEmpty();
    }

    @Test
    void failureStateCommitsWhenCallingJobTransactionRollsBack() {
        JobFixture fixture = uploaded("rollback.docx");
        JobClaim claim = claimService.claimOne(fixture.jobId()).orElseThrow();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            failureService.handle(claim, JobProcessingException.nonRetryable(
                "PARSER_FAILED",
                "문서 분석에 실패했습니다.",
                null
            ));
            throw new IllegalStateException("force calling transaction rollback");
        })).isInstanceOf(IllegalStateException.class);

        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(claim.attempt());
        assertThat(job.getClaimToken()).isNull();
        assertThat(job.getLastErrorCode()).isEqualTo("PARSER_FAILED");
        assertThat(versionStatus(fixture.jobId())).isEqualTo("FAILED");
    }

    @Test
    void staleRunningJobIsRecoveredForRetry() {
        JobFixture fixture = uploaded("stale.docx");
        claimService.claimOne(fixture.jobId()).orElseThrow();
        ageLease(fixture.jobId(), 10);

        assertThat(recoveryService.recoverStaleJobs()).isEqualTo(1);
        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(versionStatus(fixture.jobId())).isEqualTo("QUEUED");
        assertThat(job.getLastErrorCode()).isEqualTo("STALE_EXECUTION");
    }

    @Test
    void staleJobAtMaxAttemptsFails() {
        JobFixture fixture = uploaded("stale-max.docx");
        claimService.claimOne(fixture.jobId()).orElseThrow();
        jdbcClient.sql("""
            update analysis_jobs
            set attempt_count = 3,
                heartbeat_at = :old,
                claimed_at = :old
            where id = :id
            """)
            .param("old", LocalDateTime.now(jobClock).minusMinutes(10))
            .param("id", fixture.jobId())
            .update();

        assertThat(recoveryService.recoverStaleJobs()).isEqualTo(1);
        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(versionStatus(fixture.jobId())).isEqualTo("FAILED");
    }

    @Test
    void canceledJobIsNotClaimed() {
        JobFixture fixture = uploaded("canceled.docx");
        AnalysisJob job = jobRepository.findById(fixture.jobId()).orElseThrow();
        job.cancel();
        jobRepository.saveAndFlush(job);
        assertThat(claimService.claimOne(fixture.jobId())).isEmpty();
    }

    private void ageLease(Long jobId, int minutes) {
        LocalDateTime old = LocalDateTime.now(jobClock).minusMinutes(minutes);
        jdbcClient.sql("""
            update analysis_jobs set heartbeat_at = :old, claimed_at = :old where id = :id
            """)
            .param("old", old)
            .param("id", jobId)
            .update();
    }

    private String versionStatus(Long jobId) {
        return jdbcClient.sql("""
            select v.parse_status
            from analysis_jobs j
            join document_versions v on v.id = j.source_document_version_id
            where j.id = :id
            """)
            .param("id", jobId)
            .query(String.class)
            .single();
    }

    private JobFixture uploaded(String fileName) {
        Fixture fixture = project();
        var result = commandService.upload(new DocumentUploadCommand(
            fixture.project().getId(),
            fixture.owner().getId(),
            DocumentType.BUSINESS_PLAN,
            fileName,
            BusinessPlanDocxPolicy.DOCX_MIME,
            DOCX_LIKE.length,
            () -> new ByteArrayInputStream(DOCX_LIKE),
            UUID.randomUUID().toString()
        ));
        return new JobFixture(result.jobId());
    }

    private Fixture project() {
        User owner = userRepository.saveAndFlush(User.create(
            UUID.randomUUID() + "@example.com", "hashed", "owner"
        ));
        Project project = projectRepository.saveAndFlush(
            Project.create(owner, "idea", null, "AI")
        );
        return new Fixture(owner, project);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("phase1c-claim-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Fixture(User owner, Project project) {
    }

    private record JobFixture(Long jobId) {
    }
}
