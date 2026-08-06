package com.aivle.backend.job.runner;

import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.audit.AuditEventType;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobFailureService {
    private final AnalysisJobRepository jobRepository;
    private final JobExecutionProperties properties;
    private final Clock jobClock;
    private final DomainAuditService audit;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(JobClaim claim, JobProcessingException failure) {
        AnalysisJob job = jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(jobClock);
        if (failure.isRetryable() && claim.attempt() < properties.maxAttempts()) {
            Duration delay = failure.getRetryAfter() != null
                ? capped(failure.getRetryAfter())
                : backoff(claim.attempt());
            job.scheduleRetry(
                claim.claimToken(),
                claim.attempt(),
                failure.getErrorCode(),
                failure.getSafeMessage(),
                now.plus(delay),
                now
            );
            if (job.getJobType() == JobType.DOCUMENT_PARSE) {
                job.getSourceDocumentVersion().markQueuedForRetry();
            }
            return;
        }
        job.failAttempt(
            claim.claimToken(),
            claim.attempt(),
            failure.getErrorCode(),
            failure.getSafeMessage(),
            failure.isRetryable(),
            now
        );
        if (job.getJobType() == JobType.DOCUMENT_PARSE) {
            job.getSourceDocumentVersion().failProcessing();
        }
        if (job.getJobType() == JobType.LEGAL_REVIEW) {
            audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
                AuditEventType.LEGAL_REVIEW_FAILED, "AnalysisJob", job.getId(), null,
                Map.of("jobId", job.getId().toString(),
                    "safeErrorCode", failure.getErrorCode()));
        }
        if (job.getJobType() == JobType.FEASIBILITY_ANALYSIS) {
            audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
                AuditEventType.FEASIBILITY_ANALYSIS_FAILED, "AnalysisJob", job.getId(), null,
                Map.of("jobId", job.getId().toString(),
                    "safeErrorCode", failure.getErrorCode()));
        }
        if (job.getJobType() == JobType.PERSONA_RECOMMENDATION) {
            audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
                AuditEventType.PERSONA_RECOMMENDATION_FAILED, "AnalysisJob", job.getId(), null,
                Map.of("jobId", job.getId().toString(),
                    "safeErrorCode", failure.getErrorCode()));
        }
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 20);
        Duration calculated = properties.retryInitialDelay().multipliedBy(multiplier);
        return capped(calculated);
    }

    private Duration capped(Duration delay) {
        return delay.compareTo(properties.retryMaxDelay()) > 0
            ? properties.retryMaxDelay()
            : delay;
    }
}
