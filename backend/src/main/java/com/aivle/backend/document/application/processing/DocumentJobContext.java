package com.aivle.backend.document.application.processing;

import com.aivle.backend.common.entity.FileStatus;
import com.aivle.backend.common.entity.StorageType;

public record DocumentJobContext(
    Long jobId,
    Long projectId,
    Long documentId,
    Long documentVersionId,
    StorageType storageType,
    String storageKey,
    String originalFileName,
    String mimeType,
    long sizeBytes,
    String checksumSha256,
    FileStatus fileStatus,
    boolean encrypted
) {
}
