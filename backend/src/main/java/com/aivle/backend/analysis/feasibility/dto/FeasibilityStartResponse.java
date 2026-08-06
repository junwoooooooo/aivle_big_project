package com.aivle.backend.analysis.feasibility.dto;

import com.aivle.backend.common.entity.JobStatus;

public record FeasibilityStartResponse(
    Long projectId,
    Long assessmentId,
    Long jobId,
    JobStatus status,
    Long structuredPlanId,
    Long legalReviewId,
    Long sourceDocumentVersionId
) {}
