package com.aivle.backend.job.service;

import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.job.dto.response.JobResponse;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class JobQueryService {
    private final AnalysisJobRepository repository;

    public JobResponse find(Long userId, Long jobId) {
        AnalysisJob job = repository.findByIdAndProjectOwnerIdAndDeletedAtIsNull(jobId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        return toResponse(job);
    }

    public JobResponse findLatest(Long userId, Long projectId, JobType jobType) {
        if (jobType != JobType.DOCUMENT_PARSE && jobType != JobType.LEGAL_REVIEW
            && jobType != JobType.FEASIBILITY_ANALYSIS
            && jobType != JobType.PERSONA_RECOMMENDATION
            && jobType != JobType.SYSTEM_SMOKE_TEST
            && jobType != JobType.SYSTEM_ARTIFACT_SMOKE_TEST
            && jobType != JobType.MARKETING_GENERATION) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        AnalysisJob job = repository
            .findTopByProjectIdAndProjectOwnerIdAndJobTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId,
                userId,
                jobType
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        return toResponse(job);
    }

    private JobResponse toResponse(AnalysisJob job) {
        String safeMessage = job.getErrorMessage() != null
            ? job.getErrorMessage()
            : job.getCurrentStep();
        return new JobResponse(job.getId(), job.getProject().getId(), job.getJobType(), job.getStatus(),
                job.getProgress(), safeMessage, job.getAttemptCount(), job.getNextAttemptAt(),
                job.getRetryable(),
                job.getSourceDocumentVersion() == null
                    ? null
                    : job.getSourceDocumentVersion().getId(),
                job.getStartedAt(), job.getCompletedAt(),
                job.getErrorCode(),
                job.getExternalRequestId(),
                job.getResultReferenceType(),
                job.getResultReferenceId(),
                job.getRerunOfJob() == null
                    ? null
                    : job.getRerunOfJob().getId());
    }
}
