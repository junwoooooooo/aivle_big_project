package com.aivle.backend.analysis.legal.application;

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
    private final com.aivle.backend.analysis.legal.feedback.ReviewCycleService cycleService;
    private final ApplicationEventPublisher events;
    private final DomainAuditService audit;

    @Transactional
    public LegalReviewStartResponse start(Long userId, Long projectId) {
        return start(userId, projectId, null);
    }

    @Transactional
    public LegalReviewStartResponse start(
        Long userId, Long projectId, com.aivle.backend.analysis.legal.entity.ReviewMode requestedMode
    ) {
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
        var cycle = cycleService.ensureActiveCycle(project, plan);
        // 기본 모드: 사이클에 완료된 리뷰가 있으면 INCREMENTAL, 첫 검토면 FULL (§4-2)
        var mode = requestedMode != null ? requestedMode
            : (cycle.getLatestReviewId() != null
                ? com.aivle.backend.analysis.legal.entity.ReviewMode.INCREMENTAL
                : com.aivle.backend.analysis.legal.entity.ReviewMode.FULL);
        if (cycle.getLatestReviewId() == null) {
            mode = com.aivle.backend.analysis.legal.entity.ReviewMode.FULL;
        }
        cycle.beginReview(null);
        String fingerprint = sha256(projectId + ":" + plan.getId() + ":"
            + plan.getSourceDocumentVersion().getId() + ":" + LegalReviewPolicy.PROMPT_VERSION);
        String idempotencyKey = "legal:" + plan.getId() + ":" + LegalReviewPolicy.PROMPT_VERSION;

        // idempotency key는 (project, jobType)당 하나뿐이다. 실패로 끝난 작업이 남아 있으면
        // 새 row를 넣을 수 없으므로 그 row를 다시 큐에 넣는다.
        var previous = jobRepository.findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
            projectId, JobType.LEGAL_REVIEW, idempotencyKey);
        if (previous.isPresent()) {
            AnalysisJob retried = previous.get();
            retried.requeueTerminated();
            retried.assignRequestedMode(mode.name());
            audit.record(userId, projectId, AuditEventType.LEGAL_REVIEW_REQUESTED,
                "AnalysisJob", retried.getId(), null,
                Map.of("jobId", retried.getId().toString(),
                    "structuredPlanId", plan.getId().toString()));
            events.publishEvent(new LegalReviewRequested(retried.getId()));
            return response(projectId, null, retried, plan);
        }

        AnalysisJob job = AnalysisJob.queuedLegalReview(
            project, plan,
            "{\"structuredPlanId\":" + plan.getId()
                + ",\"sourceDocumentVersionId\":" + plan.getSourceDocumentVersion().getId()
                + ",\"promptVersion\":\"" + LegalReviewPolicy.PROMPT_VERSION + "\"}",
            idempotencyKey,
            fingerprint
        );
        job.assignRequestedMode(mode.name());
        job = jobRepository.save(job);
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
