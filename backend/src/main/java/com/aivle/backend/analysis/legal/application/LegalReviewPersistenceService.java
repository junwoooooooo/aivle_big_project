package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.entity.*;
import com.aivle.backend.analysis.legal.repository.*;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.integration.ai.legal.LegalReviewAiResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.audit.AuditEventType;

@Service
@RequiredArgsConstructor
public class LegalReviewPersistenceService {
    private final AnalysisJobRepository jobs;
    private final LegalReviewRepository reviews;
    private final LegalFindingRepository findings;
    private final LegalReviewQuestionRepository questions;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;
    private final DomainAuditService audit;

    @Transactional
    public Long complete(JobClaim claim, LegalReviewJobContext context, LegalReviewAiResponse result) {
        validate(result);
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        var plan = job.getSourceStructuredPlan();
        var existing = reviews.findByStructuredPlanIdAndPromptVersionAndDeletedAtIsNull(
            plan.getId(), LegalReviewPolicy.PROMPT_VERSION);
        LegalReview review;
        if (existing.isPresent()) {
            review = existing.get();
        } else {
            LocalDateTime now = LocalDateTime.now(jobClock);
            String canonical = json(result);
            review = reviews.save(LegalReview.completed(
                job.getProject(), job, plan,
                result.questions().isEmpty() ? LegalReviewStatus.COMPLETED : LegalReviewStatus.NEEDS_REVIEW,
                result.overallRiskLevel(), result.summary(), LegalReviewPolicy.DISCLAIMER,
                result.provider(), result.model(), LegalReviewPolicy.PROMPT_VERSION,
                sha256(LegalReviewPolicy.PROMPT), sha256(canonical), context.snapshotJson(), now));
            int order = 1;
            for (var item : result.findings()) {
                findings.save(LegalFinding.create(
                    review, item.category(), order++, item.applicability(), item.riskLevel(),
                    item.title(), item.finding(), item.rationale(), item.recommendedAction(),
                    json(item.evidence()), json(item.sourceSectionCodes()),
                    item.requiresProfessionalReview(), item.confidence()));
            }
            int questionOrder = 1;
            for (var question : result.questions()) {
                questions.save(LegalReviewQuestion.open(
                    review, questionOrder++, question.question(), question.reason()));
            }
            job.getProject().enterFeasibility();
        }
        job.complete(claim.claimToken(), claim.attempt(), JobStatus.SUCCEEDED,
            "LEGAL_REVIEW", review.getId(), LocalDateTime.now(jobClock));
        audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
            AuditEventType.LEGAL_REVIEW_COMPLETED, "LegalReview", review.getId(), null,
            Map.of("legalReviewId", review.getId().toString(),
                "resultStatus", review.getStatus().name(),
                "overallRiskLevel", review.getRiskLevel().name()));
        return review.getId();
    }

    private void validate(LegalReviewAiResponse result) {
        if (result == null || result.findings() == null
            || result.findings().size() != LegalCategory.values().length) {
            throw new IllegalArgumentException("legal AI result must contain exactly ten categories");
        }
        EnumSet<LegalCategory> seen = EnumSet.noneOf(LegalCategory.class);
        for (var item : result.findings()) {
            if (item == null || item.category() == null || item.applicability() == null
                || item.riskLevel() == null || !seen.add(item.category())) {
                throw new IllegalArgumentException("legal AI result contains invalid categories");
            }
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("legal result serialization failed", exception);
        }
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
