package com.aivle.backend.pipeline.artifact.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.StorageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_evidence_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectEvidenceArtifact extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(name = "storage_type", nullable = false, length = 30) private StorageType storageType;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500) private String storageKey;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "stored_filename", nullable = false, length = 255) private String storedFilename;
    @Column(name = "media_type", nullable = false, length = 150) private String mediaType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 71) private String sha256;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;

    public static ProjectEvidenceArtifact create(String id, Long projectId, StorageType storageType,
            String storageKey, String originalFilename, String storedFilename, String mediaType,
            long sizeBytes, String sha256, Long userId) {
        if (blank(id) || projectId == null || storageType == null || blank(storageKey)
                || blank(originalFilename) || blank(storedFilename) || blank(mediaType) || sizeBytes <= 0
                || sha256 == null || !sha256.matches("sha256:[0-9a-f]{64}") || userId == null) {
            throw new IllegalArgumentException("Project evidence artifact metadata is invalid");
        }
        ProjectEvidenceArtifact value = new ProjectEvidenceArtifact();
        value.id = id; value.projectId = projectId; value.storageType = storageType;
        value.storageKey = storageKey; value.originalFilename = originalFilename;
        value.storedFilename = storedFilename; value.mediaType = mediaType;
        value.sizeBytes = sizeBytes; value.sha256 = sha256; value.createdByUserId = userId;
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
