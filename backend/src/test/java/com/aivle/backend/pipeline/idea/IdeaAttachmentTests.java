package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.pipeline.idea.application.IdeaAttachmentService;
import com.aivle.backend.pipeline.idea.application.IdeaAttachmentUploadPolicy;
import com.aivle.backend.pipeline.idea.domain.IdeaAttachmentUpload;
import com.aivle.backend.pipeline.idea.repository.IdeaAttachmentUploadRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class IdeaAttachmentTests {
    private final IdeaAttachmentUploadPolicy policy = new IdeaAttachmentUploadPolicy();

    @Test
    void acceptsPurposeSpecificDocumentFormatsAndRejectsImageOrMalformedText() throws Exception {
        assertThat(policy.validate("notes.txt", new ByteArrayInputStream("시장 메모".getBytes(StandardCharsets.UTF_8)))
            .contentType()).isEqualTo("text/plain");
        assertThat(policy.validate("assumptions.md", new ByteArrayInputStream("# 가정".getBytes(StandardCharsets.UTF_8)))
            .contentType()).isEqualTo("text/markdown");
        assertThat(policy.validate("brief.docx", new ByteArrayInputStream(docx())).extension()).isEqualTo("docx");
        assertThatThrownBy(() -> policy.validate("screen.png", new ByteArrayInputStream(new byte[] {1, 2, 3})))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_UNSUPPORTED));
        assertThatThrownBy(() -> policy.validate("broken.txt", new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28})))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_SIGNATURE_INVALID));
    }

    @Test
    void resolvesOnlyOwnedProjectAttachmentAndSuppliesExtractedContent() throws Exception {
        ProjectRepository projects = mock(ProjectRepository.class);
        StoredFileRepository storedFiles = mock(StoredFileRepository.class);
        IdeaAttachmentUploadRepository uploads = mock(IdeaAttachmentUploadRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        IdeaAttachmentUpload ownership = mock(IdeaAttachmentUpload.class);
        StoredFile file = mock(StoredFile.class);
        when(uploads.findAllByStoredFileIdInAndProjectIdAndUploadedByUserIdAndDeletedAtIsNull(Set.of(19L), 41L, 7L))
            .thenReturn(List.of(ownership));
        when(storedFiles.findAllById(Set.of(19L))).thenReturn(List.of(file));
        when(file.getId()).thenReturn(19L);
        when(file.getExtension()).thenReturn("txt");
        when(file.getStorageKey()).thenReturn("projects/41/idea-brief/attachments/file.txt");
        when(file.getOriginalFilename()).thenReturn("interview.txt");
        when(file.getMimeType()).thenReturn("text/plain");
        when(storage.open(file.getStorageKey()))
            .thenReturn(new ByteArrayInputStream("고객은 빠른 비교를 원합니다.".getBytes(StandardCharsets.UTF_8)));
        IdeaAttachmentService service = new IdeaAttachmentService(projects, storedFiles, uploads, policy,
            storage, new ObjectKeyGenerator());

        List<java.util.Map<String, Object>> documents = service.resolveDocuments(7L, 41L, Set.of(19L));

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.get("attachmentFileId")).isEqualTo(19L);
            assertThat(document.get("content")).isEqualTo("고객은 빠른 비교를 원합니다.");
        });
    }

    @Test
    void rejectsAttachmentOwnedByAnotherProject() {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        IdeaAttachmentUploadRepository uploads = mock(IdeaAttachmentUploadRepository.class);
        when(uploads.findAllByStoredFileIdInAndProjectIdAndUploadedByUserIdAndDeletedAtIsNull(Set.of(19L), 41L, 7L))
            .thenReturn(List.of());
        IdeaAttachmentService service = new IdeaAttachmentService(projects, mock(StoredFileRepository.class), uploads,
            policy, mock(ObjectStoragePort.class), new ObjectKeyGenerator());
        assertThatThrownBy(() -> service.resolveDocuments(7L, 41L, Set.of(19L)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private byte[] docx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml")); zip.write("<Types/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml")); zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return output.toByteArray();
    }
}
