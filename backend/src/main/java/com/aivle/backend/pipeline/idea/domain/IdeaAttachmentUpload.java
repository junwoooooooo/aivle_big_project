package com.aivle.backend.pipeline.idea.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_attachment_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaAttachmentUpload extends BaseEntity {
    @Id @Column(name = "stored_file_id") private Long storedFileId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "uploaded_by_user_id", nullable = false) private Long uploadedByUserId;

    public static IdeaAttachmentUpload create(Long storedFileId, Long projectId, Long uploadedByUserId) {
        if (storedFileId == null || projectId == null || uploadedByUserId == null) {
            throw new IllegalArgumentException("idea attachment ownership is required");
        }
        IdeaAttachmentUpload upload = new IdeaAttachmentUpload();
        upload.storedFileId = storedFileId;
        upload.projectId = projectId;
        upload.uploadedByUserId = uploadedByUserId;
        return upload;
    }
}
