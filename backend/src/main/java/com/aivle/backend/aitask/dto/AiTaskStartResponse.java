package com.aivle.backend.aitask.dto;

import com.aivle.backend.common.entity.JobStatus;

public record AiTaskStartResponse(
    Long jobId,
    JobStatus status,
    boolean created,
    Long rerunOfJobId
) {
}
