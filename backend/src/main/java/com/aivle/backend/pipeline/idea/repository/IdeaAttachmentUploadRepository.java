package com.aivle.backend.pipeline.idea.repository;

import com.aivle.backend.pipeline.idea.domain.IdeaAttachmentUpload;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaAttachmentUploadRepository extends JpaRepository<IdeaAttachmentUpload, Long> {
    List<IdeaAttachmentUpload> findAllByStoredFileIdInAndProjectIdAndUploadedByUserIdAndDeletedAtIsNull(
        Collection<Long> storedFileIds,
        Long projectId,
        Long uploadedByUserId
    );
}
