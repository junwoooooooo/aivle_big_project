package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.analysis.feasibility.*;
import com.aivle.backend.analysis.feasibility.dto.FeasibilityStartResponse;
import com.aivle.backend.analysis.feasibility.repository.FeasibilityAssessmentRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.audit.*;
import com.aivle.backend.common.entity.*;
import com.aivle.backend.common.exception.*;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FeasibilityCommandService {
    private final ProjectRepository projects;
    private final StructuredPlanRepository plans;
    private final LegalReviewRepository legalReviews;
    private final AnalysisJobRepository jobs;
    private final FeasibilityAssessmentRepository assessments;
    private final ApplicationEventPublisher events;
    private final DomainAuditService audit;
    private final ServicePolicyService servicePolicy;

    @Transactional
    public FeasibilityStartResponse start(Long userId, Long projectId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        servicePolicy.requireDocumentProcessingEnabled();
        var project = projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        var plan = plans
            .findTopByProjectIdAndStatusAndCompletionRateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, StructuredPlanStatus.CONFIRMED, 100)
            .orElseThrow(() -> new BusinessException(ErrorCode.FEASIBILITY_INPUT_INVALID));
        var legal = legalReviews
            .findTopByProjectIdAndStructuredPlanIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, plan.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FEASIBILITY_INPUT_INVALID));
        var existing = assessments
            .findByStructuredPlanIdAndLegalReviewIdAndPromptVersionAndCatalogVersionAndDeletedAtIsNull(
                plan.getId(), legal.getId(), FeasibilityPolicy.PROMPT_VERSION,
                FeasibilityDimensionCatalog.VERSION);
        if (existing.isPresent()) {
            var assessment = existing.get();
            return response(projectId, assessment.getId(), assessment.getAnalysisJob(),
                plan.getId(), legal.getId(), plan.getSourceDocumentVersion().getId());
        }
        if (project.getStage() != ProjectStage.FEASIBILITY) {
            throw new BusinessException(ErrorCode.FEASIBILITY_INPUT_INVALID);
        }
        if (jobs.existsByProjectIdAndJobTypeAndStatusInAndDeletedAtIsNull(
            projectId, JobType.FEASIBILITY_ANALYSIS,
            List.of(JobStatus.QUEUED, JobStatus.RUNNING))) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        String source = projectId + ":" + plan.getId() + ":" + legal.getId() + ":"
            + plan.getSourceDocumentVersion().getId() + ":" + FeasibilityPolicy.PROMPT_VERSION
            + ":" + FeasibilityDimensionCatalog.VERSION;
        String fingerprint = sha256(source);
        String idempotencyKey = "feasibility:" + plan.getId() + ":" + legal.getId()
            + ":" + FeasibilityPolicy.PROMPT_VERSION;
        String requestJson = "{\"structuredPlanId\":" + plan.getId()
            + ",\"legalReviewId\":" + legal.getId()
            + ",\"sourceDocumentVersionId\":" + plan.getSourceDocumentVersion().getId()
            + ",\"promptVersion\":\"" + FeasibilityPolicy.PROMPT_VERSION
            + "\",\"catalogVersion\":\"" + FeasibilityDimensionCatalog.VERSION + "\"}";
        var job = jobs.save(AnalysisJob.queuedFeasibilityAssessment(
            project, plan, legal, requestJson, idempotencyKey, fingerprint));
        audit.record(userId, projectId, AuditEventType.FEASIBILITY_ANALYSIS_REQUESTED,
            "AnalysisJob", job.getId(), null,
            Map.of("jobId", job.getId().toString(),
                "structuredPlanId", plan.getId().toString(),
                "legalReviewId", legal.getId().toString()));
        events.publishEvent(new FeasibilityRequested(job.getId()));
        return response(projectId, null, job, plan.getId(), legal.getId(),
            plan.getSourceDocumentVersion().getId());
    }

    private FeasibilityStartResponse response(
        Long projectId, Long assessmentId, AnalysisJob job,
        Long planId, Long legalReviewId, Long sourceVersionId
    ) {
        return new FeasibilityStartResponse(
            projectId, assessmentId, job.getId(), job.getStatus(),
            planId, legalReviewId, sourceVersionId);
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
