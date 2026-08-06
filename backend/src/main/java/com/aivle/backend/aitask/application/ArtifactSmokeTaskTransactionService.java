package com.aivle.backend.aitask.application;

import com.aivle.backend.aitask.dto.AiTaskStartResponse;
import com.aivle.backend.aitask.entity.AiTaskArtifact;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArtifactSmokeTaskTransactionService {
    private final ProjectRepository projects;
    private final AnalysisJobRepository jobs;
    private final StoredFileRepository storedFiles;
    private final AiTaskArtifactRepository artifacts;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Optional<AiTaskStartResponse> findExisting(
        Long userId,
        Long projectId,
        String idempotencyKey,
        String fingerprint
    ) {
        requireOwnedProject(userId, projectId);
        return jobs
            .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                projectId,
                JobType.SYSTEM_ARTIFACT_SMOKE_TEST,
                idempotencyKey
            )
            .map(job -> reuseOrConflict(job, fingerprint));
    }

    @Transactional
    public AiTaskStartResponse create(
        Long userId,
        Long projectId,
        String idempotencyKey,
        String fingerprint,
        ObjectStoragePort.StoredObject stored,
        StorageType storageType
    ) {
        var project = projects.findByIdForUpdate(projectId)
            .filter(value ->
                value.getOwner().getId().equals(userId)
            )
            .orElseThrow(() ->
                new BusinessException(ErrorCode.PROJECT_NOT_FOUND)
            );
        var existing = jobs
            .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                projectId,
                JobType.SYSTEM_ARTIFACT_SMOKE_TEST,
                idempotencyKey
            );
        if (existing.isPresent()) {
            return reuseOrConflict(existing.get(), fingerprint);
        }

        StoredFile storedFile = storedFiles.save(
            StoredFile.available(
                storageType,
                stored.objectKey(),
                "artifact-smoke-source.json",
                filename(stored.objectKey()),
                "json",
                stored.contentType(),
                stored.sizeBytes(),
                stored.checksumSha256()
            )
        );
        AnalysisJob job = jobs.save(
            AnalysisJob.queuedSystemArtifactSmoke(
                project,
                requestJson(projectId, storedFile.getId()),
                idempotencyKey,
                fingerprint
            )
        );
        artifacts.save(AiTaskArtifact.source(job, storedFile));
        events.publishEvent(
            new ArtifactSmokeTaskRequested(job.getId())
        );
        return new AiTaskStartResponse(
            job.getId(),
            job.getStatus(),
            true,
            null
        );
    }

    private void requireOwnedProject(Long userId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(
            projectId,
            userId
        ).orElseThrow(() ->
            new BusinessException(ErrorCode.PROJECT_NOT_FOUND)
        );
    }

    private AiTaskStartResponse reuseOrConflict(
        AnalysisJob job,
        String fingerprint
    ) {
        if (!job.hasSameIdempotentRequest(fingerprint)) {
            throw new BusinessException(
                ErrorCode.IDEMPOTENCY_CONFLICT
            );
        }
        return new AiTaskStartResponse(
            job.getId(),
            job.getStatus(),
            false,
            null
        );
    }

    private String requestJson(Long projectId, Long storedFileId) {
        return "{\"projectId\":" + projectId
            + ",\"sourceStoredFileId\":" + storedFileId
            + ",\"schemaVersion\":\"1.0\"}";
    }

    private String filename(String objectKey) {
        return objectKey.substring(objectKey.lastIndexOf('/') + 1);
    }
}
