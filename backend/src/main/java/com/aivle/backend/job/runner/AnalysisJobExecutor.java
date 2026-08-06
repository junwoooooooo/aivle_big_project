package com.aivle.backend.job.runner;

import com.aivle.backend.common.entity.JobType;

public interface AnalysisJobExecutor {
    JobType jobType();
    void execute(JobClaim claim);
}
