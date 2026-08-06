package com.aivle.backend.integration.ai.dto;
import com.aivle.backend.common.entity.JobStatus;
public record AiJobStatusResponse(String externalRequestId, JobStatus status, int progress,
                                  String currentStep, String resultPayload,
                                  String errorCode, String errorMessage) {}
