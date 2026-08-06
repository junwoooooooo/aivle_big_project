package com.aivle.backend.aitask.application;

import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.integration.ai.AiServerException;
import com.aivle.backend.integration.ai.task.AiTaskGateway;
import com.aivle.backend.integration.ai.task.AiTaskType;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.job.runner.AnalysisJobExecutor;
import com.aivle.backend.job.runner.JobClaim;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ArtifactSmokeTaskJobExecutor
    implements AnalysisJobExecutor {

    private final AiTaskGateway aiTaskGateway;
    private final ArtifactSmokeTaskPersistenceService persistence;
    private final AiTaskArtifactRepository artifacts;
    private final ObjectStoragePort objectStorage;
    private final ObjectKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    @Override
    public JobType jobType() {
        return JobType.SYSTEM_ARTIFACT_SMOKE_TEST;
    }

    @Override
    public void execute(JobClaim claim) {
        persistence.markDispatched(claim);
        var source = artifacts.findByJobIdAndRole(
            claim.jobId(),
            AiArtifactRole.SOURCE
        ).orElseThrow(() ->
            new IllegalStateException(
                "source artifact does not exist"
            )
        );
        var sourceFile = source.getStoredFile();
        String outputKey = keyGenerator.aiArtifactJson();
        if (objectStorage.exists(outputKey)) {
            throw new IllegalStateException(
                "generated output object key already exists"
            );
        }
        String requestId = UUID.randomUUID().toString();
        AiTaskRequest request = new AiTaskRequest(
            requestId,
            claim.jobId().toString(),
            AiTaskType.SYSTEM_ARTIFACT_SMOKE_TEST,
            ArtifactSmokeTaskCommandService.SCHEMA_VERSION,
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            List.of(new AiTaskRequest.ArtifactInput(
                source.getId().toString(),
                "SOURCE",
                sourceFile.getStorageKey(),
                objectStorage.createPresignedGet(
                    sourceFile.getStorageKey()
                ).toString(),
                sourceFile.getMimeType(),
                sourceFile.getSizeBytes(),
                "sha256:" + sourceFile.getChecksumSha256()
            )),
            List.of(new AiTaskRequest.OutputTarget(
                "RESULT",
                outputKey,
                objectStorage.createPresignedPut(
                    outputKey,
                    ArtifactSmokeTaskCommandService.CONTENT_TYPE
                ).toString(),
                ArtifactSmokeTaskCommandService.CONTENT_TYPE
            ))
        );
        try {
            persistence.complete(
                claim,
                aiTaskGateway.execute(request),
                outputKey
            );
        } catch (AiServerException exception) {
            cleanup(outputKey);
            persistence.fail(claim, exception);
        } catch (IOException | RuntimeException exception) {
            cleanup(outputKey);
            throw new IllegalStateException(
                "AI artifact validation failed",
                exception
            );
        }
    }

    private void cleanup(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (IOException | RuntimeException ignored) {
            // The orphan reconciliation process is the fallback cleanup.
        }
    }
}
