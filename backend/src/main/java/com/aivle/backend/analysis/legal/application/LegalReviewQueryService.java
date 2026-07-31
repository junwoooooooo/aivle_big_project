package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.dto.LegalReviewResponse;
import com.aivle.backend.analysis.legal.feedback.ReviewCycleRepository;
import com.aivle.backend.analysis.legal.feedback.ReviewCycleStatus;
import com.aivle.backend.analysis.legal.feedback.ReviewDiffService;
import com.aivle.backend.analysis.legal.feedback.RevisionRequestRepository;
import com.aivle.backend.analysis.legal.feedback.RevisionSuggestionRepository;
import com.aivle.backend.analysis.legal.repository.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalReviewQueryService {
    private final LegalReviewRepository reviews;
    private final LegalFindingRepository findings;
    private final LegalReviewQuestionRepository questions;
    private final ReviewCycleRepository cycles;
    private final RevisionRequestRepository revisionRequests;
    private final RevisionSuggestionRepository suggestions;
    private final ReviewDiffService diffService;
    private final ObjectMapper objectMapper;

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
                item.getEvidenceJson(), item.getReasoningJson(), item.getSourceSectionCodesJson(),
                item.getRequiresProfessionalReview(), item.getConfidence(),
                item.getCarried()))
            .toList();
        var questionResponses = questions
            .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(review.getId())
            .stream()
            .map(item -> new LegalReviewResponse.Question(
                item.getId(), item.getDisplayOrder(), item.getQuestion(),
                item.getReason(), item.getStatus()))
            .toList();
        var diffSummary = diffService.summarize(review);
        return new LegalReviewResponse(
            review.getId(), review.getProject().getId(), review.getStructuredPlan().getId(),
            review.getSourceDocumentVersion().getId(), review.getVersionNumber(),
            review.getStatus(), review.getRiskLevel(), review.getSummary(),
            review.getDisclaimer(), review.getProvider(), review.getModelName(),
            review.getPromptVersion(), review.getCompletedAt(), itemResponses, questionResponses,
            review.getMode(), parseList(review.getRerunCategoriesJson()),
            parseList(review.getCarriedCategoriesJson()),
            diffSummary == null ? null : new LegalReviewResponse.Diff(
                diffSummary.resolved(), diffSummary.added(), diffSummary.maintained()),
            revisionRequestItems(review.getReviewCycleId()),
            cycleView(review.getReviewCycleId()));
    }

    private List<LegalReviewResponse.RevisionRequestItem> revisionRequestItems(Long cycleId) {
        if (cycleId == null) {
            return List.of();
        }
        return revisionRequests.findByReviewCycleIdAndDeletedAtIsNullOrderById(cycleId).stream()
            .map(request -> new LegalReviewResponse.RevisionRequestItem(
                request.getId(), request.getCategory(),
                request.getAnchorSectionCode().name(), request.getAnchorQuote(),
                request.getRationale(), request.getStatus().name(),
                request.getAcceptedSuggestionId(), request.getResolvedInVersion(),
                suggestions.findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(
                        request.getId()).stream()
                    .map(s -> new LegalReviewResponse.SuggestionItem(
                        s.getId(), s.getLabel(), s.getNewText()))
                    .toList()))
            .toList();
    }

    private LegalReviewResponse.Cycle cycleView(Long cycleId) {
        if (cycleId == null) {
            return null;
        }
        return cycles.findById(cycleId)
            .filter(cycle -> !cycle.isDeleted())
            .map(cycle -> new LegalReviewResponse.Cycle(
                cycle.getId(), cycle.getStatus().name(),
                cycle.getCurrentPlan().getVersionNumber(),
                cycle.getStatus() == ReviewCycleStatus.CONVERGED))
            .orElse(null);
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException exception) {
            return List.of();
        }
    }
}
