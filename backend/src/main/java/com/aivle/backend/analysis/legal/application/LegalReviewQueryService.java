package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.dto.LegalReviewResponse;
import com.aivle.backend.analysis.legal.repository.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalReviewQueryService {
    private final LegalReviewRepository reviews;
    private final LegalFindingRepository findings;
    private final LegalReviewQuestionRepository questions;

    public LegalReviewResponse latest(Long userId, Long projectId) {
        var review = reviews
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_REVIEW_NOT_FOUND));
        var itemResponses = findings
            .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(review.getId())
            .stream()
            .map(item -> new LegalReviewResponse.Finding(
                item.getId(), item.getCategory(), item.getDisplayOrder(),
                item.getApplicability(), item.getSeverity(), item.getTitle(),
                item.getDescription(), item.getRationale(), item.getRecommendation(),
                item.getEvidenceJson(), item.getSourceSectionCodesJson(),
                item.getRequiresProfessionalReview(), item.getConfidence()))
            .toList();
        var questionResponses = questions
            .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(review.getId())
            .stream()
            .map(item -> new LegalReviewResponse.Question(
                item.getId(), item.getDisplayOrder(), item.getQuestion(),
                item.getReason(), item.getStatus()))
            .toList();
        return new LegalReviewResponse(
            review.getId(), review.getProject().getId(), review.getStructuredPlan().getId(),
            review.getSourceDocumentVersion().getId(), review.getVersionNumber(),
            review.getStatus(), review.getRiskLevel(), review.getSummary(),
            review.getDisclaimer(), review.getProvider(), review.getModelName(),
            review.getPromptVersion(), review.getCompletedAt(), itemResponses, questionResponses);
    }
}
