package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.analysis.legal.dto.LegalReviewStartResponse;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.common.entity.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.audit.AuditEventType;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LegalReviewCommandService {
    private final ProjectRepository projectRepository;
    private final StructuredPlanRepository planRepository;
    private final AnalysisJobRepository jobRepository;
    private final LegalReviewRepository reviewRepository;
    private final ApplicationEventPublisher events;
    private final DomainAuditService audit;
    private final ServicePolicyService servicePolicy;

    @Transactional
    public LegalReviewStartResponse start(Long userId, Long projectId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        servicePolicy.requireDocumentProcessingEnabled();
        var project = projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        var plan = planRepository
            .findTopByProjectIdAndStatusAndCompletionRateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, StructuredPlanStatus.CONFIRMED, 100)
            .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_REVIEW_INPUT_INVALID));
        var existing = reviewRepository.findByStructuredPlanIdAndPromptVersionAndDeletedAtIsNull(
            plan.getId(), LegalReviewPolicy.PROMPT_VERSION);
        if (existing.isPresent()) {
            var review = existing.get();
            var job = review.getAnalysisJob();
            return response(projectId, review.getId(), job, plan);
        }
        if (project.getStage() != ProjectStage.LEGAL_REVIEW) {
            throw new BusinessException(ErrorCode.LEGAL_REVIEW_INPUT_INVALID);
        }
        if (jobRepository.existsByProjectIdAndJobTypeAndStatusInAndDeletedAtIsNull(
            projectId, JobType.LEGAL_REVIEW, List.of(JobStatus.QUEUED, JobStatus.RUNNING))) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        String fingerprint = sha256(projectId + ":" + plan.getId() + ":"
            + plan.getSourceDocumentVersion().getId() + ":" + LegalReviewPolicy.PROMPT_VERSION);
        AnalysisJob job = jobRepository.save(AnalysisJob.queuedLegalReview(
            project, plan,
            "{\"structuredPlanId\":" + plan.getId()
                + ",\"sourceDocumentVersionId\":" + plan.getSourceDocumentVersion().getId()
                + ",\"promptVersion\":\"" + LegalReviewPolicy.PROMPT_VERSION + "\"}",
            "legal:" + plan.getId() + ":" + LegalReviewPolicy.PROMPT_VERSION,
            fingerprint
        ));
        audit.record(userId, projectId, AuditEventType.LEGAL_REVIEW_REQUESTED,
            "AnalysisJob", job.getId(), null,
            Map.of("jobId", job.getId().toString(),
                "structuredPlanId", plan.getId().toString()));
        events.publishEvent(new LegalReviewRequested(job.getId()));
        return response(projectId, null, job, plan);
    }

    private LegalReviewStartResponse response(
        Long projectId, Long reviewId, AnalysisJob job,
        com.aivle.backend.document.entity.StructuredPlan plan
    ) {
        return new LegalReviewStartResponse(projectId, reviewId, job.getId(), job.getStatus(),
            plan.getId(), plan.getSourceDocumentVersion().getId());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
