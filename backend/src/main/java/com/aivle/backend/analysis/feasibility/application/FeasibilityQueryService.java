package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.analysis.feasibility.dto.FeasibilityAssessmentResponse;
import com.aivle.backend.analysis.feasibility.repository.*;
import com.aivle.backend.common.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeasibilityQueryService {
    private final FeasibilityAssessmentRepository assessments;
    private final FeasibilityDimensionResultRepository dimensions;
    private final FeasibilityValidationTaskRepository validationTasks;

    public FeasibilityAssessmentResponse latest(Long userId, Long projectId) {
        var assessment = assessments
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, userId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.FEASIBILITY_ASSESSMENT_NOT_FOUND));
        var dimensionResponses = dimensions
            .findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(assessment.getId())
            .stream().map(item -> new FeasibilityAssessmentResponse.Dimension(
                item.getId(), item.getDimensionCode(), item.getDisplayOrder(), item.getScore(),
                item.getConfidence(), item.getStatus(), item.getFinding(), item.getRationale(),
                item.getStrengthsJson(), item.getRisksJson(), item.getAssumptionsJson(),
                item.getEvidenceJson(), item.getSourceSectionCodesJson(),
                item.getLegalFindingIdsJson(), item.getRecommendedActionsJson())).toList();
        var taskResponses = validationTasks
            .findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(assessment.getId())
            .stream().map(item -> new FeasibilityAssessmentResponse.ValidationTask(
                item.getId(), item.getTaskCode(), item.getDimensionCode(), item.getTitle(),
                item.getDescription(), item.getReason(), item.getPriority(),
                item.getValidationMethod(), item.getExpectedEvidence(), item.getStatus(),
                item.getDisplayOrder())).toList();
        return new FeasibilityAssessmentResponse(
            assessment.getId(), assessment.getProject().getId(),
            assessment.getStructuredPlan().getId(), assessment.getLegalReview().getId(),
            assessment.getSourceDocumentVersion().getId(), assessment.getVersionNumber(),
            assessment.getStatus(), assessment.getVerdict(), assessment.getOverallScore(),
            assessment.getConfidence(), assessment.getSummary(),
            assessment.getKeyStrengthsJson(), assessment.getKeyRisksJson(),
            assessment.getDisclaimer(), assessment.getProvider(), assessment.getModelName(),
            assessment.getPromptVersion(), assessment.getCatalogVersion(),
            assessment.getCompletedAt(), dimensionResponses, taskResponses);
    }
}
