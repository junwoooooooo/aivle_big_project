package com.aivle.backend.job.runner;
import com.aivle.backend.common.entity.JobType;

public record JobClaim(Long jobId, JobType jobType, String claimToken, int attempt) {
}
