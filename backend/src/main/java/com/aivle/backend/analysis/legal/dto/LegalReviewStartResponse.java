package com.aivle.backend.analysis.legal.dto;

import com.aivle.backend.common.entity.JobStatus;

public record LegalReviewStartResponse(
    Long projectId, Long legalReviewId, Long jobId, JobStatus status,
    Long structuredPlanId, Long sourceDocumentVersionId
) {}
