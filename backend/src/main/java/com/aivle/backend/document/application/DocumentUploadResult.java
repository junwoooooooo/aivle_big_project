package com.aivle.backend.document.application;

import com.aivle.backend.common.entity.JobStatus;

public record DocumentUploadResult(
    Long projectId,
    Long documentId,
    Long versionId,
    Long jobId,
    JobStatus status,
    boolean created
) {
}
