package com.aivle.backend.document.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.document.entity.ProjectDocument;
import com.aivle.backend.document.repository.DocumentVersionRepository;
import com.aivle.backend.document.repository.ProjectDocumentRepository;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DocumentUploadTransactionServiceTests {
    @Mock ProjectRepository projectRepository;
    @Mock ProjectDocumentRepository documentRepository;
    @Mock DocumentVersionRepository versionRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock AnalysisJobRepository jobRepository;
    @Mock ApplicationEventPublisher events;
    @Mock ObjectStoragePort storage;

    private DocumentUploadTransactionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentUploadTransactionService(
            projectRepository,
            documentRepository,
            versionRepository,
            storedFileRepository,
            jobRepository,
            events,
            storage,
            new ObjectKeyGenerator()
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager
            .isSynchronizationActive()) {
            TransactionSynchronizationManager
                .clearSynchronization();
        }
    }

    @Test
    void deletesObjectWhenDatabaseWorkFailsAfterUpload()
        throws Exception {
        User owner = User.create(
            "owner@example.com",
            "hash",
            "owner"
        );
        ReflectionTestUtils.setField(owner, "id", 22L);
        Project project = Project.create(
            owner,
            "project",
            "",
            "food"
        );
        ReflectionTestUtils.setField(project, "id", 11L);

        when(projectRepository.findByIdForUpdate(11L))
            .thenReturn(Optional.of(project));
        when(documentRepository
            .findAllByProjectIdAndDocumentTypeAndStatusAndDeletedAtIsNull(
                any(), any(), any()
            ))
            .thenReturn(List.of());
        when(documentRepository.save(any()))
            .thenAnswer(invocation -> {
                ProjectDocument document = invocation.getArgument(0);
                ReflectionTestUtils.setField(
                    document,
                    "id",
                    33L
                );
                return document;
            });
        when(storedFileRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any()))
            .thenAnswer(invocation -> {
                var version = invocation.getArgument(
                    0,
                    com.aivle.backend.document.entity
                        .DocumentVersion.class
                );
                ReflectionTestUtils.setField(
                    version,
                    "id",
                    44L
                );
                return version;
            });
        when(storage.storageType())
            .thenReturn(StorageType.S3_COMPATIBLE);
        when(storage.store(any(), anyLong(), any(), any()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(3);
                return new ObjectStoragePort.StoredObject(
                    key,
                    4,
                    "application/vnd.openxmlformats-officedocument"
                        + ".wordprocessingml.document",
                    "a".repeat(64)
                );
            });
        when(storage.metadata(any()))
            .thenAnswer(invocation ->
                new ObjectStoragePort.ObjectMetadata(
                    invocation.getArgument(0),
                    4,
                    "application/vnd.openxmlformats-officedocument"
                        + ".wordprocessingml.document"
                )
            );
        when(jobRepository.save(any()))
            .thenThrow(new IllegalStateException("db failed"));

        ValidatedUpload upload = new ValidatedUpload(
            new byte[] {1, 2, 3, 4},
            "plan.docx",
            "docx",
            "application/vnd.openxmlformats-officedocument"
                + ".wordprocessingml.document",
            "a".repeat(64)
        );
        DocumentUploadCommand command =
            new DocumentUploadCommand(
                11L,
                22L,
                DocumentType.BUSINESS_PLAN,
                "plan.docx",
                upload.contentType(),
                4,
                () -> new ByteArrayInputStream(
                    new byte[] {1, 2, 3, 4}
                ),
                "key"
            );

        assertThatThrownBy(() -> service.create(
            command,
            upload,
            "key",
            "fingerprint"
        )).isInstanceOf(IllegalStateException.class);

        var synchronizations = TransactionSynchronizationManager
            .getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(
            TransactionSynchronization.STATUS_ROLLED_BACK
        ));

        verify(storage).delete(
            org.mockito.ArgumentMatchers.argThat(key ->
                key.startsWith(
                    "projects/11/documents/33/versions/44/source/"
                )
            )
        );
    }
}
