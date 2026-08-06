package com.aivle.backend.aitask.application;

import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.object.ObjectStoragePort;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiTaskArtifactDownloadService {
    private final AiTaskArtifactRepository artifacts;
    private final ObjectStoragePort objectStorage;
    private final ArtifactIntegrityService integrity;

    @Transactional(readOnly = true)
    public DownloadedArtifact downloadResult(
        Long userId,
        Long projectId,
        Long jobId
    ) {
        var artifact = artifacts.findOwnedByJobAndRole(
            jobId,
            projectId,
            userId,
            AiArtifactRole.RESULT
        ).orElseThrow(() ->
            new BusinessException(ErrorCode.JOB_NOT_FOUND)
        );
        var file = artifact.getStoredFile();
        try {
            byte[] content = integrity.verify(
                objectStorage,
                file.getStorageKey(),
                file.getMimeType(),
                file.getSizeBytes(),
                "sha256:" + file.getChecksumSha256()
            ).content();
            return new DownloadedArtifact(
                artifact.getId(),
                file.getStoredFilename(),
                file.getMimeType(),
                content
            );
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.FILE_STORAGE_FAILED
            );
        }
    }

    public record DownloadedArtifact(
        Long artifactId,
        String filename,
        String contentType,
        byte[] content
    ) {
    }
}
