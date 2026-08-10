package com.aivle.backend.pipeline.artifact;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.pipeline.artifact.application.EvidenceArtifactUploadPolicy;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.artifact.config.EvidenceArtifactProperties;
import com.aivle.backend.pipeline.artifact.domain.ProjectEvidenceArtifact;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class ProjectEvidenceArtifactTests {
    @Test
    void uploadOwnedProjectSanitizesTraversalNameAndPersistsHash() throws Exception {
        byte[] content = "%PDF-1.4\nevidence".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectEvidenceArtifactRepository artifacts = mock(ProjectEvidenceArtifactRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        when(storage.storageType()).thenReturn(StorageType.LOCAL);
        when(storage.store(any(), eq((long) content.length), eq("application/pdf"), anyString()))
            .thenAnswer(invocation -> new ObjectStoragePort.StoredObject(invocation.getArgument(3),
                content.length, "application/pdf", checksum));
        when(artifacts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new ProjectEvidenceArtifactService(projects, artifacts,
            policy(DataSize.ofMegabytes(1)), storage, new ObjectKeyGenerator());

        var view = service.upload(7L, 41L, new MockMultipartFile("file", "../../quote.pdf",
            "application/x-msdownload", content));

        assertThat(view.originalFilename()).isEqualTo("quote.pdf");
        assertThat(view.sha256()).isEqualTo("sha256:" + checksum);
        var capture = org.mockito.ArgumentCaptor.forClass(ProjectEvidenceArtifact.class);
        verify(artifacts).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getStorageKey()).matches("projects/41/evidence/[0-9a-f-]{36}/[0-9a-f-]{36}\\.pdf");
        assertThat(capture.getValue().getStoredFilename()).doesNotContain("quote", "..", "/", "\\");
    }

    @Test
    void foreignProjectIsDeniedBeforeStorage() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        var service = new ProjectEvidenceArtifactService(projects, mock(ProjectEvidenceArtifactRepository.class),
            policy(DataSize.ofMegabytes(1)), storage, new ObjectKeyGenerator());

        assertThatThrownBy(() -> service.upload(7L, 41L,
            new MockMultipartFile("file", "quote.pdf", "application/pdf", "%PDF-x".getBytes())))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        verifyNoInteractions(storage);
    }

    @Test
    void invalidExtensionAndOversizedContentAreDenied() {
        EvidenceArtifactUploadPolicy normal = policy(DataSize.ofMegabytes(1));
        assertThatThrownBy(() -> normal.validate("payload.exe", "application/pdf",
            new ByteArrayInputStream("%PDF-x".getBytes())))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED));

        EvidenceArtifactUploadPolicy tiny = policy(DataSize.ofBytes(5));
        assertThatThrownBy(() -> tiny.validate("notes.txt", "text/plain",
            new ByteArrayInputStream("123456".getBytes())))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void deletedArtifactCannotBeDownloaded() {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        var service = new ProjectEvidenceArtifactService(projects, mock(ProjectEvidenceArtifactRepository.class),
            policy(DataSize.ofMegabytes(1)), mock(ObjectStoragePort.class), new ObjectKeyGenerator());

        assertThatThrownBy(() -> service.download(7L, 41L, "deleted"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ARTIFACT_NOT_FOUND));
    }

    private static EvidenceArtifactUploadPolicy policy(DataSize size) {
        return new EvidenceArtifactUploadPolicy(new EvidenceArtifactProperties(size,
            List.of("pdf", "csv", "xlsx", "xls", "docx", "txt", "png", "jpg", "jpeg")));
    }
}
