package com.aivle.backend.persona.recommendation.dto;

import com.aivle.backend.common.entity.JobStatus;

public record PersonaStartResponse(
    Long projectId,
    Long recommendationId,
    Long jobId,
    JobStatus status,
    Long structuredPlanId,
    Long feasibilityAssessmentId,
    String catalogVersion
) {}
