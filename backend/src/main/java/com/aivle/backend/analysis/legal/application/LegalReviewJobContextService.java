package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.analysis.legal.entity.ReviewMode;
import com.aivle.backend.analysis.legal.feedback.*;
import com.aivle.backend.analysis.legal.repository.LegalFindingRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewQuestionRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.document.repository.StructuredPlanSectionRepository;
import com.aivle.backend.integration.ai.legal.LegalReviewAiRequest;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import com.aivle.backend.job.runner.JobProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LegalReviewJobContextService {
    private final AnalysisJobRepository jobRepository;
    private final StructuredPlanSectionRepository sectionRepository;
    private final ReviewCycleRepository cycleRepository;
    private final LegalReviewRepository reviewRepository;
    private final LegalFindingRepository findingRepository;
    private final LegalReviewQuestionRepository questionRepository;
    private final ConfirmedFactRepository factRepository;
    private final IncrementalReviewPlanner planner;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LegalReviewJobContext load(JobClaim claim) {
        var job = jobRepository.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> invalid("LEGAL_JOB_NOT_FOUND"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())
            || job.getSourceStructuredPlan() == null) {
            throw invalid("LEGAL_JOB_CLAIM_LOST");
        }
        var plan = job.getSourceStructuredPlan();
        if (!plan.getProject().getId().equals(job.getProject().getId())
            || plan.getStatus() != com.aivle.backend.common.entity.StructuredPlanStatus.CONFIRMED
            || plan.getCompletionRate() != 100) {
            throw invalid("LEGAL_INPUT_SNAPSHOT_INVALID");
        }
        var sectionEntities = sectionRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(plan.getId());
        var sections = sectionEntities.stream()
            .map(section -> new LegalReviewAiRequest.Section(
                section.getSectionCode().name(), section.getTitle(),
                section.getSourceText(), section.getEvidenceJson()))
            .toList();

        var cycle = cycleRepository
            .findTopByProjectIdAndStatusNotAndDeletedAtIsNullOrderByIdDesc(
                job.getProject().getId(), ReviewCycleStatus.PUBLISHED)
            .orElse(null);
        Long cycleId = cycle == null ? null : cycle.getId();
        LegalReview parentReview = (cycle != null && cycle.getLatestReviewId() != null)
            ? reviewRepository.findById(cycle.getLatestReviewId()).orElse(null)
            : null;

        List<LegalReviewAiRequest.ConfirmedFactPayload> factPayloads = cycleId == null
            ? List.of()
            : factRepository.findByReviewCycleIdAndDeletedAtIsNullOrderByAnsweredAt(cycleId).stream()
                .map(fact -> new LegalReviewAiRequest.ConfirmedFactPayload(
                    fact.getFactKey(), fact.getFactValue(), fact.getSource(),
                    String.valueOf(fact.getAnsweredAt())))
                .toList();

        ReviewMode mode = parseMode(job.getRequestedMode());
        List<String> changedSections = List.of();
        Set<LegalCategory> rerunCategories = EnumSet.allOf(LegalCategory.class);
        Set<LegalCategory> carriedCategories = EnumSet.noneOf(LegalCategory.class);
        if (mode == ReviewMode.INCREMENTAL && parentReview != null) {
            var parentSections = sectionRepository
                .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(
                    parentReview.getStructuredPlan().getId());
            var parentFindings = findingRepository
                .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(parentReview.getId());
            List<String> newFactCategories = new ArrayList<>();
            int parentPlanVersion = parentReview.getStructuredPlan().getVersionNumber();
            for (var fact : factRepository
                .findByReviewCycleIdAndDeletedAtIsNullOrderByAnsweredAt(cycleId)) {
                if (fact.getCreatedInPlan().getVersionNumber() > parentPlanVersion
                    && fact.getFromQuestionId() != null) {
                    questionRepository.findById(fact.getFromQuestionId())
                        .ifPresent(question -> newFactCategories.add(question.getCategoriesJson()));
                }
            }
            var incremental = planner.plan(
                sectionEntities, parentSections, parentFindings, newFactCategories);
            if (incremental.degradedToFull()) {
                mode = ReviewMode.FULL;
            } else {
                changedSections = incremental.changedSections();
                rerunCategories = incremental.rerunCategories();
                carriedCategories = incremental.carriedCategories();
            }
        } else {
            mode = ReviewMode.FULL;
        }

        try {
            return new LegalReviewJobContext(job.getProject().getId(), plan.getId(),
                plan.getSourceDocumentVersion().getId(), sections,
                objectMapper.writeValueAsString(sections),
                mode, cycleId,
                parentReview == null ? null : parentReview.getId(),
                changedSections, rerunCategories, carriedCategories, factPayloads);
        } catch (JacksonException exception) {
            throw invalid("LEGAL_INPUT_SERIALIZATION_FAILED");
        }
    }

    private ReviewMode parseMode(String requestedMode) {
        if (requestedMode == null) {
            return ReviewMode.FULL;
        }
        try {
            return ReviewMode.valueOf(requestedMode);
        } catch (IllegalArgumentException exception) {
            return ReviewMode.FULL;
        }
    }

    private JobProcessingException invalid(String code) {
        return JobProcessingException.nonRetryable(
            code, "법률·규제 사전검토 입력을 확인할 수 없습니다.", null);
    }
}
