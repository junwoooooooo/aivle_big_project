package com.aivle.backend.document.application;

import com.aivle.backend.common.entity.DocumentType;

public record DocumentUploadCommand(
    Long projectId,
    Long userId,
    DocumentType documentType,
    String originalFilename,
    String contentType,
    long declaredSize,
    UploadContent content,
    String idempotencyKey
) {
}
