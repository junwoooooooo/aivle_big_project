package com.aivle.backend.job.dto.response;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.entity.JobType;

import java.time.LocalDateTime;

public record JobResponse(
    Long jobId,
    Long projectId,
    JobType jobType,
    JobStatus status,
    int progress,
    String message,
    int attempt,
    LocalDateTime nextAttemptAt,
    Boolean retryable,
    Long sourceDocumentVersionId,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String errorCode,
    String externalRequestId,
    String resultReferenceType,
    Long resultReferenceId,
    Long rerunOfJobId
) {
}
