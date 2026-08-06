package com.aivle.backend.integration.ai.dto;
import com.aivle.backend.common.entity.JobStatus;
public record AiJobAcceptedResponse(String externalRequestId, JobStatus status) {}
