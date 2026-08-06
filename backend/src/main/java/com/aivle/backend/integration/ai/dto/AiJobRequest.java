package com.aivle.backend.integration.ai.dto;
import com.aivle.backend.common.entity.JobType;
import java.time.Instant;
public record AiJobRequest(Long jobId, Long projectId, JobType jobType, String inputVersion,
                           String inputPayload, String traceId, Instant deadlineAt) {}
