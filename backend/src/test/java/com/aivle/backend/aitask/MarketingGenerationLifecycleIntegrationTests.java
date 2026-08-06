package com.aivle.backend.aitask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.aitask.repository.AiTaskResultRepository;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.AiTaskGateway;
import com.aivle.backend.integration.ai.task.AiTaskType;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaimService;
import com.aivle.backend.marketing.content.MarketingContent;
import com.aivle.backend.marketing.content.MarketingContentRepository;
import com.aivle.backend.marketing.content.MarketingContentTypes.*;
import com.aivle.backend.marketing.content.MarketingContentVersion;
import com.aivle.backend.marketing.content.MarketingContentVersionRepository;
import com.aivle.backend.marketing.generation.MarketingGenerationCommandService;
import com.aivle.backend.marketing.generation.MarketingGenerationJobExecutor;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(MarketingGenerationLifecycleIntegrationTests.Configuration.class)
class MarketingGenerationLifecycleIntegrationTests {
    @Autowired MarketingGenerationCommandService commands;
    @Autowired MarketingGenerationJobExecutor executor;
    @Autowired JobClaimService claims;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AiTaskResultRepository results;
    @Autowired AiTaskArtifactRepository artifacts;
    @Autowired MarketingContentRepository contents;
    @Autowired MarketingContentVersionRepository versions;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired AiArtifactLifecycleIntegrationTests.FakeObjectStorage storage;
    @Autowired MarketingGateway gateway;

    @BeforeEach
    void reset() {
        gateway.failure = null;
        gateway.lastRequest = null;
    }

    @Test
    void createsRunsAndPersistsGeneratedVersionAndArtifact() {
        Fixture fixture = fixture();
        var accepted = commands.start(
            fixture.userId(), fixture.projectId(), fixture.contentId(),
            fixture.versionId(), "marketing-success", image());
        var duplicate = commands.start(
            fixture.userId(), fixture.projectId(), fixture.contentId(),
            fixture.versionId(), " marketing-success ", image());

        assertThat(accepted.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(duplicate.jobId()).isEqualTo(accepted.jobId());
        assertThat(duplicate.created()).isFalse();
        executor.execute(claims.claimOne(accepted.jobId()).orElseThrow());

        var job = jobs.findById(accepted.jobId()).orElseThrow();
        var generated = versions.findByAnalysisJobId(job.getId()).orElseThrow();
        var source = versions.findById(fixture.versionId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(results.findByAnalysisJobIdAndDeletedAtIsNull(job.getId()))
            .isPresent();
        assertThat(generated.getVersionNumber()).isEqualTo(2);
        assertThat(generated.getHeadline()).isEqualTo(source.getHeadline());
        assertThat(source.getAnalysisJob()).isNull();
        assertThat(artifacts.findByJobIdAndRole(
            job.getId(), AiArtifactRole.RESULT)).isPresent();
        assertThat(gateway.lastRequest.taskType())
            .isEqualTo(AiTaskType.MARKETING_BANNER_GENERATION);
        assertThat(gateway.lastRequest.taskId())
            .isEqualTo(job.getId().toString());
        assertThat(gateway.lastRequest.input().get("main_banner").asText())
            .isEqualTo(source.getHeadline());
    }

    @Test
    void failureDoesNotCreateVersionAndOtherOwnerCannotStart() {
        Fixture fixture = fixture();
        Fixture other = fixture();
        assertThatThrownBy(() -> commands.start(
            other.userId(), fixture.projectId(), fixture.contentId(),
            fixture.versionId(), "other-owner", image()
        )).isInstanceOf(BusinessException.class);

        var accepted = commands.start(
            fixture.userId(), fixture.projectId(), fixture.contentId(),
            fixture.versionId(), "marketing-failure", image());
        gateway.failure = new AiServerException(
            500, "AI_SERVER_INTERNAL_ERROR", true,
            "remote-request", "Safe AI failure", new RuntimeException("secret"));
        executor.execute(claims.claimOne(accepted.jobId()).orElseThrow());

        var failed = jobs.findById(accepted.jobId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Safe AI failure");
        assertThat(versions.findByAnalysisJobId(failed.getId())).isEmpty();
        assertThat(versions
            .findAllByMarketingContentIdOrderByVersionNumberDesc(
                fixture.contentId())).hasSize(1);
    }

    @Test
    void explicitRerunCreatesNewJobAndVersionAndPreservesOriginals() {
        Fixture fixture = fixture();
        var first = commands.start(
            fixture.userId(), fixture.projectId(), fixture.contentId(),
            fixture.versionId(), "marketing-first", image());
        executor.execute(claims.claimOne(first.jobId()).orElseThrow());
        Long firstVersionId = versions.findByAnalysisJobId(first.jobId())
            .orElseThrow().getId();

        var rerun = commands.rerun(
            fixture.userId(), fixture.projectId(), fixture.contentId(),
            first.jobId(), "marketing-rerun");
        executor.execute(claims.claimOne(rerun.jobId()).orElseThrow());

        assertThat(rerun.jobId()).isNotEqualTo(first.jobId());
        assertThat(rerun.rerunOfJobId()).isEqualTo(first.jobId());
        assertThat(versions.findById(firstVersionId)).isPresent();
        assertThat(versions.findByAnalysisJobId(rerun.jobId())).isPresent();
        assertThat(versions
            .findAllByMarketingContentIdOrderByVersionNumberDesc(
                fixture.contentId())).hasSize(3);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create(
            "marketing-" + suffix + "@example.com", "hashed", "user"));
        Project project = projects.saveAndFlush(Project.create(
            user, "marketing-" + suffix, "generation", "test"));
        MarketingContent content = contents.saveAndFlush(
            MarketingContent.create(
                project, user, "Campaign", Purpose.AWARENESS,
                Channel.DISPLAY_AD, Format.LANDSCAPE_1200_628,
                1200, 628, null, null, "{}", null, null, 1));
        MarketingContentVersion version = versions.saveAndFlush(
            MarketingContentVersion.create(
                content, 1, user,
                new MarketingContentVersion.Draft(
                    "Headline", "Supporting", "Body", "Learn more",
                    "Evidence", Tone.PROFESSIONAL, "BLUE",
                    Template.HERO_CENTER, BackgroundType.SOLID,
                    "#ffffff", "#0055ff", "#111111",
                    TextAlignment.CENTER, 64, true, false, "{}"),
                LocalDateTime.now(), 1, true, true));
        return new Fixture(
            user.getId(), project.getId(), content.getId(), version.getId());
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
            "image", "source.png", "image/png", "mock-image".getBytes());
    }

    record Fixture(
        Long userId, Long projectId, Long contentId, Long versionId
    ) {
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        AiArtifactLifecycleIntegrationTests.FakeObjectStorage storage() {
            return new AiArtifactLifecycleIntegrationTests.FakeObjectStorage();
        }

        @Bean
        @Primary
        MarketingGateway marketingGateway(
            AiArtifactLifecycleIntegrationTests.FakeObjectStorage storage,
            ObjectMapper objectMapper
        ) {
            return new MarketingGateway(storage, objectMapper);
        }
    }

    static class MarketingGateway implements AiTaskGateway {
        private final AiArtifactLifecycleIntegrationTests.FakeObjectStorage storage;
        private final ObjectMapper objectMapper;
        private AiTaskRequest lastRequest;
        private AiServerException failure;

        MarketingGateway(
            AiArtifactLifecycleIntegrationTests.FakeObjectStorage storage,
            ObjectMapper objectMapper
        ) {
            this.storage = storage;
            this.objectMapper = objectMapper;
        }

        @Override
        public AiTaskResponse execute(AiTaskRequest request) {
            lastRequest = request;
            if (failure != null) throw failure;
            var source = request.artifacts().get(0);
            var target = request.outputTargets().get(0);
            try {
                byte[] content = storage.open(source.objectKey()).readAllBytes();
                storage.put(target.objectKey(), content, target.contentType());
                String checksum = sha256(content);
                return new AiTaskResponse(
                    request.requestId(), request.taskId(), request.taskType(),
                    "SUCCEEDED", request.schemaVersion(),
                    objectMapper.createObjectNode().put("mock", true),
                    List.of(),
                    new AiTaskResponse.Execution("marketing-banner", "1.0"),
                    null,
                    List.of(new AiTaskResponse.ArtifactMetadata(
                        "RESULT", target.objectKey(), target.contentType(),
                        content.length, "sha256:" + checksum))
                );
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private String sha256(byte[] content) {
            try {
                return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
