package com.aivle.backend.marketing.generation;

import com.aivle.backend.common.entity.JobStatus;

public record MarketingGenerationStartResponse(
    Long jobId,
    JobStatus status,
    Long contentId,
    Long sourceVersionId,
    boolean created,
    Long rerunOfJobId
) {
}
