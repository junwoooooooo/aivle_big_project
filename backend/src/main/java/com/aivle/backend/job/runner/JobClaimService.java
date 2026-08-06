package com.aivle.backend.job.runner;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobClaimService {
    private final AnalysisJobRepository jobRepository;
    private final JobExecutionProperties properties;
    private final JobWorkerIdentity workerIdentity;
    private final Clock jobClock;

    @Transactional
    public List<JobClaim> claimBatch() {
        LocalDateTime now = LocalDateTime.now(jobClock);
        return jobRepository.findClaimCandidates(
                List.of(JobType.DOCUMENT_PARSE, JobType.LEGAL_REVIEW,
                    JobType.FEASIBILITY_ANALYSIS, JobType.PERSONA_RECOMMENDATION,
                    JobType.SYSTEM_SMOKE_TEST, JobType.SYSTEM_ARTIFACT_SMOKE_TEST,
                    JobType.MARKETING_GENERATION),
                JobStatus.QUEUED,
                now,
                PageRequest.of(0, properties.batchSize())
            )
            .stream()
            .map(job -> claim(job, now))
            .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<JobClaim> claimOne(Long jobId) {
        LocalDateTime now = LocalDateTime.now(jobClock);
        return jobRepository.findByIdForUpdate(jobId)
            .filter(job -> job.getJobType() == JobType.DOCUMENT_PARSE
                || job.getJobType() == JobType.LEGAL_REVIEW
                || job.getJobType() == JobType.FEASIBILITY_ANALYSIS
                || job.getJobType() == JobType.PERSONA_RECOMMENDATION
                || job.getJobType() == JobType.SYSTEM_SMOKE_TEST
                || job.getJobType() == JobType.SYSTEM_ARTIFACT_SMOKE_TEST
                || job.getJobType() == JobType.MARKETING_GENERATION)
            .filter(job -> job.getStatus() == JobStatus.QUEUED)
            .filter(job -> switch (job.getJobType()) {
                case DOCUMENT_PARSE -> job.getSourceDocumentVersion() != null;
                case LEGAL_REVIEW -> job.getSourceStructuredPlan() != null;
                case FEASIBILITY_ANALYSIS ->
                    job.getSourceStructuredPlan() != null && job.getSourceLegalReview() != null;
                case PERSONA_RECOMMENDATION ->
                    job.getSourceStructuredPlan() != null
                        && job.getSourceFeasibilityAssessment() != null;
                case SYSTEM_SMOKE_TEST, SYSTEM_ARTIFACT_SMOKE_TEST,
                    MARKETING_GENERATION -> true;
                default -> false;
            })
            .filter(job -> job.getNextAttemptAt() == null
                || !job.getNextAttemptAt().isAfter(now))
            .map(job -> claim(job, now));
    }

    private JobClaim claim(AnalysisJob job, LocalDateTime now) {
        String token = UUID.randomUUID().toString();
        job.claim(workerIdentity.value(), token, now);
        if (job.getJobType() == JobType.DOCUMENT_PARSE) {
            job.getSourceDocumentVersion().markRunning();
        }
        return new JobClaim(job.getId(), job.getJobType(), token, job.getAttemptCount());
    }
}
