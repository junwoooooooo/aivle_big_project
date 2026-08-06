package com.aivle.backend.aitask.application;

import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.entity.AiTaskArtifact;
import com.aivle.backend.aitask.entity.AiTaskResult;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.aitask.repository.AiTaskResultRepository;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ArtifactSmokeTaskPersistenceService {
    private static final String RESULT_REFERENCE_TYPE =
        "AI_TASK_RESULT";

    private final AnalysisJobRepository jobs;
    private final AiTaskResultRepository results;
    private final AiTaskArtifactRepository artifacts;
    private final StoredFileRepository storedFiles;
    private final ObjectStoragePort objectStorage;
    private final ArtifactIntegrityService integrity;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;

    @Transactional
    public void markDispatched(JobClaim claim) {
        requireCurrent(claim).advance(
            claim.claimToken(),
            claim.attempt(),
            50,
            "AI artifact smoke dispatched",
            LocalDateTime.now(jobClock)
        );
    }

    @Transactional
    public void complete(
        JobClaim claim,
        AiTaskResponse response,
        String expectedOutputKey
    ) throws IOException {
        var job = requireCurrent(claim);
        var metadata = requireResultArtifact(
            response,
            expectedOutputKey
        );
        var verified = integrity.verify(
            objectStorage,
            expectedOutputKey,
            ArtifactSmokeTaskCommandService.CONTENT_TYPE,
            metadata.size(),
            metadata.checksum()
        );
        StoredFile outputFile = storedFiles.save(
            StoredFile.available(
                objectStorage.storageType(),
                expectedOutputKey,
                "artifact-smoke-result.json",
                filename(expectedOutputKey),
                "json",
                verified.contentType(),
                verified.content().length,
                verified.checksumSha256()
            )
        );
        AiTaskResult result = results.save(
            AiTaskResult.completed(
                job,
                response.schemaVersion(),
                response.requestId(),
                resultJson(response),
                response.execution().handler(),
                response.execution().handlerVersion()
            )
        );
        AiTaskArtifact source = artifacts
            .findByJobIdAndRole(
                claim.jobId(),
                AiArtifactRole.SOURCE
            )
            .orElseThrow(() ->
                new IllegalStateException(
                    "source artifact does not exist"
                )
            );
        source.attachResult(result);
        artifacts.save(
            AiTaskArtifact.result(job, result, outputFile)
        );
        job.setExternalRequestId(
            claim.claimToken(),
            claim.attempt(),
            response.requestId()
        );
        job.complete(
            claim.claimToken(),
            claim.attempt(),
            JobStatus.SUCCEEDED,
            RESULT_REFERENCE_TYPE,
            result.getId(),
            LocalDateTime.now(jobClock)
        );
    }

    @Transactional
    public void fail(JobClaim claim, AiServerException failure) {
        var job = requireCurrent(claim);
        if (
            failure.getRequestId() != null
            && !failure.getRequestId().isBlank()
        ) {
            job.setExternalRequestId(
                claim.claimToken(),
                claim.attempt(),
                failure.getRequestId()
            );
        }
        job.failAttempt(
            claim.claimToken(),
            claim.attempt(),
            failure.getErrorCode(),
            failure.getSafeMessage(),
            failure.isRetryable(),
            LocalDateTime.now(jobClock)
        );
    }

    private AiTaskResponse.ArtifactMetadata requireResultArtifact(
        AiTaskResponse response,
        String expectedOutputKey
    ) throws IOException {
        if (
            response.artifacts() == null
            || response.artifacts().size() != 1
        ) {
            throw new IOException(
                "AI task result artifact is missing"
            );
        }
        var artifact = response.artifacts().get(0);
        if (
            !"RESULT".equals(artifact.role())
            || !expectedOutputKey.equals(artifact.objectKey())
            || !ArtifactSmokeTaskCommandService.CONTENT_TYPE.equals(
                artifact.contentType()
            )
        ) {
            throw new IOException(
                "AI task result artifact contract mismatch"
            );
        }
        return artifact;
    }

    private com.aivle.backend.job.entity.AnalysisJob requireCurrent(
        JobClaim claim
    ) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() ->
                new IllegalStateException("job does not exist")
            );
        if (!job.hasCurrentClaim(
            claim.claimToken(),
            claim.attempt()
        )) {
            throw new IllegalStateException(
                "job claim is no longer current"
            );
        }
        return job;
    }

    private String resultJson(AiTaskResponse response) {
        try {
            return objectMapper.writeValueAsString(
                response.result()
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "AI task result serialization failed",
                exception
            );
        }
    }

    private String filename(String objectKey) {
        return objectKey.substring(objectKey.lastIndexOf('/') + 1);
    }
}
