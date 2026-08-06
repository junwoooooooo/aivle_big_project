package com.aivle.backend.document.dto.response;

import com.aivle.backend.common.entity.DocumentStatus;
import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.document.entity.ProjectDocument;

import java.time.LocalDateTime;

public record DocumentSummaryResponse(
    Long documentId,
    DocumentType documentType,
    int currentVersion,
    DocumentStatus status,
    Long latestVersionId,
    LocalDateTime updatedAt
) {
    public static DocumentSummaryResponse from(ProjectDocument document, Long latestVersionId) {
        return new DocumentSummaryResponse(
            document.getId(),
            document.getDocumentType(),
            document.getCurrentVersion(),
            document.getStatus(),
            latestVersionId,
            document.getUpdatedAt()
        );
    }
}
