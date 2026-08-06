package com.aivle.backend.job.dto.response;
import com.aivle.backend.common.entity.JobStatus;
public record JobProgressResponse(Long id, JobStatus status, int progress, String currentStep) {}
