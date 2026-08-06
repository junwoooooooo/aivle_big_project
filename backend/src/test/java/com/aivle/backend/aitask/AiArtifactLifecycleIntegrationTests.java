package com.aivle.backend.aitask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.aitask.application.ArtifactIntegrityService;
import com.aivle.backend.aitask.application.ArtifactSmokeTaskCommandService;
import com.aivle.backend.aitask.application.ArtifactSmokeTaskJobExecutor;
import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.aitask.repository.AiTaskResultRepository;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.integration.ai.task.AiTaskGateway;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaimService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiArtifactLifecycleIntegrationTests.Configuration.class)
class AiArtifactLifecycleIntegrationTests {
    @Autowired ArtifactSmokeTaskCommandService commands;
    @Autowired ArtifactSmokeTaskJobExecutor executor;
    @Autowired ArtifactIntegrityService integrity;
    @Autowired JobClaimService claims;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AiTaskResultRepository results;
    @Autowired AiTaskArtifactRepository artifacts;
    @Autowired StoredFileRepository storedFiles;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired FakeObjectStorage storage;
    @Autowired ArtifactGateway gateway;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void reset() {
        storage.objects.clear();
        gateway.lastRequest = null;
    }

    @Test
    void storesMetadataRunsTaskAndDownloadsResult() throws Exception {
        Fixture fixture = fixture();
        var accepted = commands.start(
            fixture.userId(),
            fixture.projectId(),
            "artifact-success"
        );
        var duplicate = commands.start(
            fixture.userId(),
            fixture.projectId(),
            " artifact-success "
        );

        assertThat(accepted.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(duplicate.jobId()).isEqualTo(accepted.jobId());
        assertThat(duplicate.created()).isFalse();
        var source = artifacts.findByJobIdAndRole(
            accepted.jobId(),
            AiArtifactRole.SOURCE
        ).orElseThrow();
        assertThat(source.getStoredFile().getStorageType())
            .isEqualTo(StorageType.S3_COMPATIBLE);
        assertThat(source.getStoredFile().getChecksumSha256())
            .hasSize(64);

        executor.execute(
            claims.claimOne(accepted.jobId()).orElseThrow()
        );

        var job = jobs.findById(accepted.jobId()).orElseThrow();
        var result = results
            .findByAnalysisJobIdAndDeletedAtIsNull(job.getId())
            .orElseThrow();
        var output = artifacts.findByJobIdAndRole(
            job.getId(),
            AiArtifactRole.RESULT
        ).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getResultReferenceId())
            .isEqualTo(result.getId());
        assertThat(output.getAiTaskResult().getId())
            .isEqualTo(result.getId());
        assertThat(output.getStoredFile().getMimeType())
            .isEqualTo("application/json");
        assertThat(gateway.lastRequest.artifacts()).hasSize(1);
        assertThat(gateway.lastRequest.outputTargets()).hasSize(1);
        assertThat(gateway.lastRequest.taskId())
            .isEqualTo(job.getId().toString());

        mockMvc.perform(get(
                "/api/v1/projects/{projectId}/ai-tasks/{jobId}/artifacts/result",
                fixture.projectId(),
                job.getId()
            ).header("X-User-Id", fixture.userId()))
            .andExpect(status().isOk())
            .andExpect(header().string(
                "Content-Type",
                "application/json"
            ))
            .andExpect(header().exists("X-Artifact-Id"));

        Fixture other = fixture();
        mockMvc.perform(get(
                "/api/v1/projects/{projectId}/ai-tasks/{jobId}/artifacts/result",
                fixture.projectId(),
                job.getId()
            ).header("X-User-Id", other.userId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateKeysInvalidContentTypeAndOversize()
        throws Exception {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        storage.store(
            new ByteArrayInputStream(content),
            content.length,
            "application/json",
            "ai-artifacts/duplicate.json"
        );
        assertThatThrownBy(() -> storage.store(
            new ByteArrayInputStream(content),
            content.length,
            "application/json",
            "ai-artifacts/duplicate.json"
        )).isInstanceOf(IOException.class);

        storage.put(
            "ai-artifacts/wrong-type.json",
            content,
            "text/plain"
        );
        assertThatThrownBy(() -> integrity.verify(
            storage,
            "ai-artifacts/wrong-type.json",
            "application/json",
            content.length,
            "sha256:" + sha256(content)
        )).isInstanceOf(IOException.class)
            .hasMessageContaining("content type");

        byte[] oversized = new byte[1024 * 1024 + 1];
        storage.put(
            "ai-artifacts/oversized.json",
            oversized,
            "application/json"
        );
        assertThatThrownBy(() -> integrity.verify(
            storage,
            "ai-artifacts/oversized.json",
            "application/json",
            oversized.length,
            "sha256:" + sha256(oversized)
        )).isInstanceOf(IOException.class)
            .hasMessageContaining("size");
    }

    @Test
    void artifactApiCreatesMetadataBeforeExecution()
        throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(post(
                "/api/v1/projects/{projectId}/ai-tasks/artifact-smoke",
                fixture.projectId()
            )
                .header("X-User-Id", fixture.userId())
                .header("Idempotency-Key", "artifact-api"))
            .andExpect(status().isAccepted());
        assertThat(storedFiles.count()).isPositive();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.create(
            "artifact-" + suffix + "@example.com",
            "hashed",
            "artifact-user"
        ));
        Project project = projects.saveAndFlush(Project.create(
            user,
            "artifact-" + suffix,
            "artifact lifecycle",
            "test"
        ));
        return new Fixture(user.getId(), project.getId());
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Fixture(Long userId, Long projectId) {
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        FakeObjectStorage fakeObjectStorage() {
            return new FakeObjectStorage();
        }

        @Bean
        @Primary
        ArtifactGateway artifactGateway(
            FakeObjectStorage storage,
            ObjectMapper objectMapper
        ) {
            return new ArtifactGateway(storage, objectMapper);
        }
    }

    static class ArtifactGateway implements AiTaskGateway {
        private final FakeObjectStorage storage;
        private final ObjectMapper objectMapper;
        private AiTaskRequest lastRequest;

        ArtifactGateway(
            FakeObjectStorage storage,
            ObjectMapper objectMapper
        ) {
            this.storage = storage;
            this.objectMapper = objectMapper;
        }

        @Override
        public AiTaskResponse execute(AiTaskRequest request) {
            lastRequest = request;
            var target = request.outputTargets().get(0);
            byte[] output = "{\"status\":\"processed\"}".getBytes(
                StandardCharsets.UTF_8
            );
            storage.put(
                target.objectKey(),
                output,
                target.contentType()
            );
            return new AiTaskResponse(
                request.requestId(),
                request.taskId(),
                request.taskType(),
                "SUCCEEDED",
                request.schemaVersion(),
                objectMapper.createObjectNode()
                    .put("ok", true),
                List.of(),
                new AiTaskResponse.Execution(
                    "system-artifact-smoke",
                    "1.0"
                ),
                null,
                List.of(new AiTaskResponse.ArtifactMetadata(
                    "RESULT",
                    target.objectKey(),
                    target.contentType(),
                    output.length,
                    "sha256:" + sha256(output)
                ))
            );
        }
    }

    static class FakeObjectStorage implements ObjectStoragePort {
        private final Map<String, Entry> objects =
            new ConcurrentHashMap<>();

        @Override
        public StoredObject store(
            InputStream input,
            long expectedSize,
            String contentType,
            String objectKey
        ) throws IOException {
            byte[] content = input.readAllBytes();
            if (content.length != expectedSize) {
                throw new IOException("size mismatch");
            }
            if (objects.putIfAbsent(
                objectKey,
                new Entry(content, contentType)
            ) != null) {
                throw new IOException("duplicate object key");
            }
            return new StoredObject(
                objectKey,
                content.length,
                contentType,
                sha256(content)
            );
        }

        void put(
            String key,
            byte[] content,
            String contentType
        ) {
            objects.put(key, new Entry(content, contentType));
        }

        @Override
        public InputStream open(String objectKey) throws IOException {
            Entry entry = require(objectKey);
            return new ByteArrayInputStream(entry.content());
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }

        @Override
        public boolean exists(String objectKey) {
            return objects.containsKey(objectKey);
        }

        @Override
        public ObjectMetadata metadata(String objectKey)
            throws IOException {
            Entry entry = require(objectKey);
            return new ObjectMetadata(
                objectKey,
                entry.content().length,
                entry.contentType()
            );
        }

        @Override
        public URI createPresignedGet(String objectKey) {
            return URI.create(
                "http://127.0.0.1:9000/test/" + objectKey
            );
        }

        @Override
        public URI createPresignedPut(
            String objectKey,
            String contentType
        ) {
            return URI.create(
                "http://127.0.0.1:9000/test/" + objectKey
            );
        }

        @Override
        public StorageType storageType() {
            return StorageType.S3_COMPATIBLE;
        }

        private Entry require(String key) throws IOException {
            Entry entry = objects.get(key);
            if (entry == null) {
                throw new IOException("object not found");
            }
            return entry;
        }

        record Entry(byte[] content, String contentType) {
        }
    }
}
