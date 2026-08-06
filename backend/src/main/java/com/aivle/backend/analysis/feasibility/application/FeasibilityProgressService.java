package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
@RequiredArgsConstructor
public class FeasibilityProgressService {
    private final AnalysisJobRepository jobs;
    private final Clock jobClock;

    @Transactional
    public void advance(JobClaim claim, int progress, String step) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        job.advance(claim.claimToken(), claim.attempt(), progress, step, LocalDateTime.now(jobClock));
    }

    @Transactional
    public void providerResponded(JobClaim claim, String requestId) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        job.setExternalRequestId(claim.claimToken(), claim.attempt(), requestId);
        job.advance(claim.claimToken(), claim.attempt(), 70, "AI_RESPONDED",
            LocalDateTime.now(jobClock));
    }
}
