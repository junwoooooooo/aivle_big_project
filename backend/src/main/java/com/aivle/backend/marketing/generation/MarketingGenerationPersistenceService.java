package com.aivle.backend.marketing.generation;

import com.aivle.backend.aitask.application.ArtifactIntegrityService;
import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.entity.AiTaskArtifact;
import com.aivle.backend.aitask.entity.AiTaskResult;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.aitask.repository.AiTaskResultRepository;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.config.FileStorageProperties;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.AiTaskType;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import com.aivle.backend.marketing.content.MarketingContentRepository;
import com.aivle.backend.marketing.content.MarketingContentVersion;
import com.aivle.backend.marketing.content.MarketingContentVersionRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MarketingGenerationPersistenceService {
    private final AnalysisJobRepository jobs;
    private final AiTaskResultRepository results;
    private final AiTaskArtifactRepository artifacts;
    private final StoredFileRepository storedFiles;
    private final MarketingContentRepository contents;
    private final MarketingContentVersionRepository versions;
    private final ObjectStoragePort storage;
    private final ArtifactIntegrityService integrity;
    private final FileStorageProperties fileProperties;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;

    @Transactional
    public ExecutionContext markDispatched(JobClaim claim) {
        var job = requireCurrent(claim);
        job.advance(
            claim.claimToken(), claim.attempt(), 50,
            "Marketing generation dispatched",
            LocalDateTime.now(jobClock));
        JsonNode snapshot = read(job.getRequestJson());
        return new ExecutionContext(snapshot.get("input"));
    }

    @Transactional
    public void complete(
        JobClaim claim,
        AiTaskResponse response,
        String expectedOutputKey,
        String originalFilename,
        String extension
    ) throws IOException {
        var job = requireCurrent(claim);
        validateResponse(claim, response);
        var metadata = requireArtifact(response, expectedOutputKey);
        var verified = integrity.verify(
            storage, expectedOutputKey, metadata.contentType(),
            metadata.size(), metadata.checksum(),
            fileProperties.imageMaxSize().toBytes());
        StoredFile output = storedFiles.save(StoredFile.available(
            storage.storageType(), expectedOutputKey,
            "generated-" + originalFilename,
            filename(expectedOutputKey), extension,
            verified.contentType(), verified.content().length,
            verified.checksumSha256()
        ));
        AiTaskResult result = results.save(AiTaskResult.completed(
            job, response.schemaVersion(), response.requestId(),
            json(response.result()), response.execution().handler(),
            response.execution().handlerVersion()
        ));
        AiTaskArtifact source = artifacts.findByJobIdAndRole(
            claim.jobId(), AiArtifactRole.SOURCE
        ).orElseThrow();
        source.attachResult(result);
        artifacts.save(AiTaskArtifact.result(job, result, output));

        JsonNode snapshot = read(job.getRequestJson());
        Long contentId = snapshot.get("contentId").asLong();
        Long sourceVersionId = snapshot.get("sourceVersionId").asLong();
        var content = contents
            .findForUpdateByIdAndProjectIdAndDeletedAtIsNull(
                contentId, job.getProject().getId())
            .orElseThrow();
        var sourceVersion = versions.findByIdAndMarketingContentId(
            sourceVersionId, contentId).orElseThrow();
        int number = content.advanceVersion();
        versions.save(MarketingContentVersion.generated(
            content, number, content.getCreatedBy(),
            sourceVersion.toDraft(), LocalDateTime.now(jobClock),
            content.getSourceSnapshotVersion(), job
        ));
        job.setExternalRequestId(
            claim.claimToken(), claim.attempt(), response.requestId());
        job.complete(
            claim.claimToken(), claim.attempt(), JobStatus.SUCCEEDED,
            "AI_TASK_RESULT", result.getId(), LocalDateTime.now(jobClock));
    }

    @Transactional
    public void fail(JobClaim claim, AiServerException failure) {
        var job = requireCurrent(claim);
        if (failure.getRequestId() != null
            && !failure.getRequestId().isBlank()) {
            job.setExternalRequestId(
                claim.claimToken(), claim.attempt(), failure.getRequestId());
        }
        job.failAttempt(
            claim.claimToken(), claim.attempt(),
            failure.getErrorCode(), failure.getSafeMessage(),
            failure.isRetryable(), LocalDateTime.now(jobClock));
    }

    private void validateResponse(JobClaim claim, AiTaskResponse response)
        throws IOException {
        if (!claim.jobId().toString().equals(response.taskId())
            || response.taskType() != AiTaskType.MARKETING_BANNER_GENERATION
            || !"SUCCEEDED".equals(response.status())
            || !MarketingGenerationCommandService.SCHEMA_VERSION.equals(
                response.schemaVersion())) {
            throw new IOException("marketing AI task response contract mismatch");
        }
    }

    private AiTaskResponse.ArtifactMetadata requireArtifact(
        AiTaskResponse response, String key
    ) throws IOException {
        if (response.artifacts() == null || response.artifacts().size() != 1) {
            throw new IOException("marketing result artifact is missing");
        }
        var artifact = response.artifacts().get(0);
        if (!"RESULT".equals(artifact.role())
            || !key.equals(artifact.objectKey())) {
            throw new IOException("marketing result artifact contract mismatch");
        }
        return artifact;
    }

    private com.aivle.backend.job.entity.AnalysisJob requireCurrent(
        JobClaim claim
    ) {
        var job = jobs.findByIdForUpdate(claim.jobId()).orElseThrow();
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        return job;
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "marketing request snapshot is invalid", exception);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "marketing result serialization failed", exception);
        }
    }

    private String filename(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    public record ExecutionContext(JsonNode input) {
    }
}
