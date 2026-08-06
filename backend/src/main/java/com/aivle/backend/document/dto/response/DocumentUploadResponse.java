package com.aivle.backend.document.dto.response;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.document.application.DocumentUploadResult;

public record DocumentUploadResponse(
    Long projectId,
    Long documentId,
    Long versionId,
    Long jobId,
    JobStatus status
) {
    public static DocumentUploadResponse from(DocumentUploadResult result) {
        return new DocumentUploadResponse(
            result.projectId(),
            result.documentId(),
            result.versionId(),
            result.jobId(),
            result.status()
        );
    }
}
