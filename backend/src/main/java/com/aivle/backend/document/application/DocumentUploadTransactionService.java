package com.aivle.backend.document.application;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.ProjectDocument;
import com.aivle.backend.document.repository.DocumentVersionRepository;
import com.aivle.backend.document.repository.ProjectDocumentRepository;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadTransactionService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final StoredFileRepository storedFileRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectStoragePort objectStorage;
    private final ObjectKeyGenerator objectKeys;

    @Transactional(readOnly = true)
    public void authorizeUpload(Long projectId, Long userId, DocumentType documentType) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        assertOwner(project, userId);
        assertUploadAllowed(project, documentType);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentUploadResult> findExisting(
        Long projectId,
        String idempotencyKey,
        String fingerprint
    ) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return analysisJobRepository
            .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                projectId,
                JobType.DOCUMENT_PARSE,
                idempotencyKey
            )
            .map(job -> reuseOrConflict(job, fingerprint));
    }

    @Transactional
    public DocumentUploadResult create(
        DocumentUploadCommand command,
        ValidatedUpload upload,
        String idempotencyKey,
        String fingerprint
    ) {
        Project project = projectRepository.findByIdForUpdate(command.projectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        assertOwner(project, command.userId());
        assertUploadAllowed(project, command.documentType());

        if (idempotencyKey != null) {
            Optional<AnalysisJob> existing = analysisJobRepository
                .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                    command.projectId(),
                    JobType.DOCUMENT_PARSE,
                    idempotencyKey
                );
            if (existing.isPresent()) {
                return reuseOrConflict(existing.get(), fingerprint);
            }
        }

        ProjectDocument document = resolveActiveDocument(project, command.documentType());
        StoredFile storedFile = storedFileRepository.save(
            StoredFile.available(
            objectStorage.storageType(),
            "pending/document-source/" + UUID.randomUUID(),
            upload.originalFilename(),
            UUID.randomUUID() + "." + upload.extension(),
            upload.extension(),
            upload.contentType(),
            upload.sizeBytes(),
            upload.checksumSha256()
        ));
        int versionNumber = document.allocateNextVersion();
        DocumentVersion version = documentVersionRepository.save(
            DocumentVersion.uploaded(document, versionNumber, storedFile, project.getOwner())
        );
        documentVersionRepository.flush();
        String storageKey = objectKeys.documentSource(
            project.getId(),
            document.getId(),
            version.getId(),
            upload.extension()
        );
        storeSource(upload, storageKey);
        storedFile.assignStorageKey(
            storageKey,
            Path.of(storageKey).getFileName().toString()
        );

        AnalysisJob job = analysisJobRepository.save(AnalysisJob.queuedDocumentParse(
            project,
            version,
            requestJson(project.getId(), document.getId(), version.getId()),
            idempotencyKey,
            fingerprint
        ));
        analysisJobRepository.flush();
        eventPublisher.publishEvent(new DocumentProcessingRequested(job.getId()));

        return new DocumentUploadResult(
            project.getId(),
            document.getId(),
            version.getId(),
            job.getId(),
            job.getStatus(),
            true
        );
    }

    private void storeSource(
        ValidatedUpload upload,
        String storageKey
    ) {
        try (InputStream input = upload.openStream()) {
            ObjectStoragePort.StoredObject stored = objectStorage.store(
                input,
                upload.sizeBytes(),
                upload.contentType(),
                storageKey
            );
            registerRollbackCleanup(storageKey);
            ObjectStoragePort.ObjectMetadata metadata =
                objectStorage.metadata(storageKey);
            if (stored.sizeBytes() != upload.sizeBytes()
                || metadata.sizeBytes() != upload.sizeBytes()
                || !upload.contentType().equals(stored.contentType())
                || !upload.contentType().equals(metadata.contentType())
                || !upload.checksumSha256().equals(
                    stored.checksumSha256()
                )) {
                throw new BusinessException(
                    ErrorCode.FILE_STORAGE_FAILED
                );
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteBestEffort(storageKey);
            throw new BusinessException(
                ErrorCode.FILE_STORAGE_FAILED
            );
        }
    }

    private void deleteBestEffort(String storageKey) {
        try {
            objectStorage.delete(storageKey);
        } catch (IOException | RuntimeException cleanupFailure) {
            log.error(
                "Failed to clean failed document object {}",
                storageKey,
                cleanupFailure
            );
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        return;
                    }
                    try {
                        objectStorage.delete(storageKey);
                    } catch (IOException | RuntimeException exception) {
                        // The storage reconciliation job is the crash and
                        // cleanup-failure fallback for unreferenced objects.
                        log.error(
                            "Failed to compensate rolled back document object {}",
                            storageKey,
                            exception
                        );
                    }
                }
            }
        );
    }

    private ProjectDocument resolveActiveDocument(Project project, DocumentType documentType) {
        List<ProjectDocument> active = projectDocumentRepository
            .findAllByProjectIdAndDocumentTypeAndStatusAndDeletedAtIsNull(
                project.getId(),
                documentType,
                DocumentStatus.ACTIVE
            );
        if (active.size() > 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (active.size() == 1) {
            return active.get(0);
        }
        return projectDocumentRepository.save(ProjectDocument.create(project, documentType));
    }

    private DocumentUploadResult reuseOrConflict(AnalysisJob job, String fingerprint) {
        if (!job.hasSameIdempotentRequest(fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        DocumentVersion version = job.getSourceDocumentVersion();
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        return new DocumentUploadResult(
            job.getProject().getId(),
            version.getDocument().getId(),
            version.getId(),
            job.getId(),
            job.getStatus(),
            false
        );
    }

    private void assertOwner(Project project, Long userId) {
        if (userId == null || !project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private void assertUploadAllowed(Project project, DocumentType documentType) {
        boolean supportedType = documentType == DocumentType.BUSINESS_PLAN;
        boolean statusAllowed = project.getStatus() == ProjectStatus.DRAFT
            || project.getStatus() == ProjectStatus.ACTIVE;
        boolean stageAllowed = project.getStage() == ProjectStage.DOCUMENT
            || project.getStage() == ProjectStage.STRUCTURING;
        if (!supportedType || !statusAllowed || !stageAllowed) {
            throw new BusinessException(ErrorCode.DOCUMENT_UPLOAD_NOT_ALLOWED);
        }
    }

    private String requestJson(Long projectId, Long documentId, Long versionId) {
        return "{\"projectId\":" + projectId
            + ",\"documentId\":" + documentId
            + ",\"documentVersionId\":" + versionId
            + "}";
    }
}
