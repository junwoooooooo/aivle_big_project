package com.aivle.backend.pipeline.idea.application;

import static com.aivle.backend.pipeline.idea.api.IdeaBriefApiModels.AttachmentView;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.pipeline.idea.domain.IdeaAttachmentUpload;
import com.aivle.backend.pipeline.idea.repository.IdeaAttachmentUploadRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class IdeaAttachmentService {
    private static final int MAX_TEXT_PER_FILE = 20_000;
    private static final int MAX_TEXT_TOTAL = 60_000;
    private final ProjectRepository projects;
    private final StoredFileRepository storedFiles;
    private final IdeaAttachmentUploadRepository uploads;
    private final IdeaAttachmentUploadPolicy policy;
    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;

    @Transactional
    public AttachmentView upload(Long ownerId, Long projectId, MultipartFile file) {
        requireOwned(ownerId, projectId);
        String key = null;
        try {
            ValidatedUpload validated = policy.validate(file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getInputStream());
            key = keys.ideaAttachment(projectId, UUID.randomUUID().toString(), validated.extension());
            ObjectStoragePort.StoredObject stored = storage.store(validated.openStream(), validated.sizeBytes(),
                validated.contentType(), key);
            if (stored.sizeBytes() != validated.sizeBytes()
                    || !stored.checksumSha256().equals(validated.checksumSha256())) {
                throw new IOException("stored idea attachment integrity mismatch");
            }
            registerRollbackCleanup(key);
            StoredFile storedFile = storedFiles.saveAndFlush(StoredFile.available(storage.storageType(), key,
                validated.originalFilename(), key.substring(key.lastIndexOf('/') + 1), validated.extension(),
                validated.contentType(), stored.sizeBytes(), stored.checksumSha256()));
            IdeaAttachmentUpload upload = uploads.saveAndFlush(
                IdeaAttachmentUpload.create(storedFile.getId(), projectId, ownerId));
            return new AttachmentView(storedFile.getId(), storedFile.getOriginalFilename(), storedFile.getMimeType(),
                storedFile.getSizeBytes(), upload.getCreatedAt());
        } catch (BusinessException exception) {
            cleanupQuietly(key); throw exception;
        } catch (IOException | RuntimeException exception) {
            cleanupQuietly(key); throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> resolveDocuments(Long ownerId, Long projectId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 20 || ids.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "첨부 파일 참조가 올바르지 않습니다.");
        }
        requireOwned(ownerId, projectId);
        Set<Long> uniqueIds = Set.copyOf(ids);
        List<IdeaAttachmentUpload> owned = uploads
            .findAllByStoredFileIdInAndProjectIdAndUploadedByUserIdAndDeletedAtIsNull(uniqueIds, projectId, ownerId);
        if (owned.size() != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이 프로젝트에서 업로드한 첨부 파일만 사용할 수 있습니다.");
        }
        Map<Long, StoredFile> filesById = new HashMap<>();
        storedFiles.findAllById(uniqueIds).forEach(file -> filesById.put(file.getId(), file));
        if (filesById.size() != uniqueIds.size()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);

        List<Map<String, Object>> documents = new ArrayList<>();
        int remaining = MAX_TEXT_TOTAL;
        for (Long id : uniqueIds.stream().sorted(Comparator.naturalOrder()).toList()) {
            StoredFile file = filesById.get(id);
            String text = extract(file);
            int limit = Math.min(MAX_TEXT_PER_FILE, remaining);
            if (limit <= 0) break;
            String bounded = text.length() <= limit ? text : text.substring(0, limit);
            remaining -= bounded.length();
            documents.add(Map.of(
                "attachmentFileId", id,
                "filename", file.getOriginalFilename(),
                "mediaType", file.getMimeType(),
                "content", bounded
            ));
        }
        return documents;
    }

    private String extract(StoredFile file) {
        try (InputStream input = storage.open(file.getStorageKey())) {
            if ("docx".equals(file.getExtension())) {
                try (XWPFDocument document = new XWPFDocument(input);
                        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText().strip();
                }
            }
            return new String(input.readNBytes(Math.toIntExact(IdeaAttachmentUploadPolicy.MAX_SIZE_BYTES + 1)),
                StandardCharsets.UTF_8).strip();
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
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
        try { storage.delete(key); } catch (IOException | RuntimeException ignored) { }
    }
}
