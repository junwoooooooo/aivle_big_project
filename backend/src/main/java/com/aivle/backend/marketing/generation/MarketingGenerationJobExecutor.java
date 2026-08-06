package com.aivle.backend.marketing.generation;

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
public class MarketingGenerationJobExecutor
    implements AnalysisJobExecutor {

    private final AiTaskGateway gateway;
    private final MarketingGenerationPersistenceService persistence;
    private final AiTaskArtifactRepository artifacts;
    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;
    private final ObjectMapper objectMapper;

    @Override
    public JobType jobType() {
        return JobType.MARKETING_GENERATION;
    }

    @Override
    public void execute(JobClaim claim) {
        var context = persistence.markDispatched(claim);
        var source = artifacts.findByJobIdAndRole(
            claim.jobId(), AiArtifactRole.SOURCE
        ).orElseThrow(() -> new IllegalStateException(
            "marketing source artifact does not exist"));
        var file = source.getStoredFile();
        String outputKey = keys.aiArtifactImage(file.getExtension());
        String requestId = UUID.randomUUID().toString();
        AiTaskRequest request = new AiTaskRequest(
            requestId,
            claim.jobId().toString(),
            AiTaskType.MARKETING_BANNER_GENERATION,
            MarketingGenerationCommandService.SCHEMA_VERSION,
            context.input(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            List.of(new AiTaskRequest.ArtifactInput(
                source.getId().toString(),
                "SOURCE",
                file.getStorageKey(),
                storage.createPresignedGet(file.getStorageKey()).toString(),
                file.getMimeType(),
                file.getSizeBytes(),
                "sha256:" + file.getChecksumSha256()
            )),
            List.of(new AiTaskRequest.OutputTarget(
                "RESULT",
                outputKey,
                storage.createPresignedPut(
                    outputKey, file.getMimeType()).toString(),
                file.getMimeType()
            ))
        );
        try {
            persistence.complete(
                claim, gateway.execute(request), outputKey,
                file.getOriginalFilename(), file.getExtension());
        } catch (AiServerException exception) {
            cleanup(outputKey);
            persistence.fail(claim, exception);
        } catch (IOException | RuntimeException exception) {
            cleanup(outputKey);
            throw new IllegalStateException(
                "marketing AI result validation failed", exception);
        }
    }

    private void cleanup(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException ignored) {
            // Orphan reconciliation remains the fallback.
        }
    }
}
