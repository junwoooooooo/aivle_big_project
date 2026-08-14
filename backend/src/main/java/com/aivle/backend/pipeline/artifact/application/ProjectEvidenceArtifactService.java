package com.aivle.backend.pipeline.artifact.application;

import static com.aivle.backend.pipeline.artifact.api.ProjectEvidenceArtifactApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectEvidenceArtifactService {
    private final ProjectRepository projects;
    private final ProjectEvidenceArtifactRepository artifacts;
    private final EvidenceArtifactUploadPolicy policy;
    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;

    @Transactional
    public ArtifactView upload(Long ownerId, Long projectId, MultipartFile file) {
        requireOwned(ownerId, projectId);
        String key = null;
        try {
            ValidatedUpload validated = policy.validate(file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getContentType(), file == null ? null : file.getInputStream());
            String artifactId = UUID.randomUUID().toString();
            key = keys.projectEvidence(projectId, artifactId, validated.extension());
            ObjectStoragePort.StoredObject stored = storage.store(validated.openStream(), validated.sizeBytes(),
                validated.contentType(), key);
            if (stored.sizeBytes() != validated.sizeBytes()
                    || !stored.checksumSha256().equals(validated.checksumSha256())) {
                throw new IOException("stored evidence integrity mismatch");
            }
            registerRollbackCleanup(key);
            String storedFilename = key.substring(key.lastIndexOf('/') + 1);
            ProjectEvidenceArtifact artifact = artifacts.saveAndFlush(ProjectEvidenceArtifact.create(
                artifactId, projectId, storage.storageType(), key, validated.originalFilename(), storedFilename,
                validated.contentType(), stored.sizeBytes(), "sha256:" + stored.checksumSha256(), ownerId));
            return view(artifact);
        } catch (BusinessException exception) {
            cleanupQuietly(key);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            cleanupQuietly(key);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Transactional
    public ArtifactView storeGenerated(Long ownerId, Long projectId, String filename,
            String mediaType, byte[] content) {
        requireOwned(ownerId, projectId);
        String key = null;
        try {
            ValidatedUpload validated = policy.validate(filename, mediaType,
                new ByteArrayInputStream(content == null ? new byte[0] : content));
            String artifactId = UUID.randomUUID().toString();
            key = keys.projectEvidence(projectId, artifactId, validated.extension());
            ObjectStoragePort.StoredObject stored = storage.store(validated.openStream(), validated.sizeBytes(),
                validated.contentType(), key);
            if (stored.sizeBytes() != validated.sizeBytes()
                    || !stored.checksumSha256().equals(validated.checksumSha256())) {
                throw new IOException("stored generated artifact integrity mismatch");
            }
            registerRollbackCleanup(key);
            ProjectEvidenceArtifact artifact = artifacts.saveAndFlush(ProjectEvidenceArtifact.create(
                artifactId, projectId, storage.storageType(), key, validated.originalFilename(),
                key.substring(key.lastIndexOf('/') + 1), validated.contentType(), stored.sizeBytes(),
                "sha256:" + stored.checksumSha256(), ownerId));
            return view(artifact);
        } catch (BusinessException exception) {
            cleanupQuietly(key);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            cleanupQuietly(key);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public ResolvedArtifact resolveOwned(Long ownerId, Long projectId, String artifactId, long maxBytes) {
        requireOwned(ownerId, projectId);
        ProjectEvidenceArtifact artifact = requireArtifact(projectId, artifactId);
        if (artifact.getSizeBytes() > maxBytes || !artifact.getMediaType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
        }
        try (InputStream input = storage.open(artifact.getStorageKey())) {
            byte[] content = input.readNBytes(Math.toIntExact(maxBytes + 1));
            if (content.length != artifact.getSizeBytes() || content.length > maxBytes) {
                throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
            }
            return new ResolvedArtifact(view(artifact), content);
        } catch (BusinessException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED); }
    }

    @Transactional(readOnly = true)
    public Download download(Long ownerId, Long projectId, String artifactId) {
        requireOwned(ownerId, projectId);
        ProjectEvidenceArtifact artifact = requireArtifact(projectId, artifactId);
        return open(artifact);
    }

    @Transactional(readOnly = true)
    public Download downloadForAi(Long projectId, String artifactId) {
        return open(requireArtifact(projectId, artifactId));
    }

    private Download open(ProjectEvidenceArtifact artifact) {
        try {
            if (!storage.exists(artifact.getStorageKey())) {
                throw new BusinessException(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND);
            }
            return new Download(view(artifact), storage.open(artifact.getStorageKey()));
        } catch (BusinessException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED); }
    }

    @Transactional
    public void delete(Long ownerId, Long projectId, String artifactId) {
        requireOwned(ownerId, projectId);
        requireArtifact(projectId, artifactId).softDelete();
    }

    @Transactional(readOnly = true)
    public ProjectEvidenceArtifact requireReferenceable(Long ownerId, Long projectId, String artifactId) {
        requireOwned(ownerId, projectId);
        return requireArtifact(projectId, artifactId);
    }

    private ProjectEvidenceArtifact requireArtifact(Long projectId, String artifactId) {
        return artifacts.findByIdAndProjectIdAndDeletedAtIsNull(artifactId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ArtifactView view(ProjectEvidenceArtifact value) {
        return new ArtifactView(value.getId(), value.getProjectId(), value.getOriginalFilename(),
            value.getMediaType(), value.getSizeBytes(), value.getSha256(), value.getCreatedAt());
    }

    private void registerRollbackCleanup(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) cleanupQuietly(key);
            }
        });
    }

    private void cleanupQuietly(String key) {
        if (key == null) return;
        try { storage.delete(key); } catch (IOException | RuntimeException ignored) { /* reconciliation fallback */ }
    }

    public record Download(ArtifactView artifact, InputStream content) {}
    public record ResolvedArtifact(ArtifactView artifact, byte[] content) {}
}
