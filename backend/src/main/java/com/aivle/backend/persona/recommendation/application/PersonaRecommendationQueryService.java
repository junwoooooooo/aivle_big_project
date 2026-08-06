package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.common.exception.*;
import com.aivle.backend.persona.catalog.dto.BaselinePersonaResponse;
import com.aivle.backend.persona.recommendation.dto.PersonaRecommendationResponse;
import com.aivle.backend.persona.recommendation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonaRecommendationQueryService {
    private final PersonaRecommendationRepository recommendations;
    private final PersonaRecommendationItemRepository items;
    private final CustomerHypothesisRepository hypotheses;
    private final CustomerValidationPlanRepository validationPlans;
    private final PersonaValidationTaskLinkRepository taskLinks;

    public PersonaRecommendationResponse latest(Long userId, Long projectId) {
        var recommendation = recommendations
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, userId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.PERSONA_RECOMMENDATION_NOT_FOUND));
        var itemResponses = items
            .findByRecommendationIdAndDeletedAtIsNullOrderByRank(recommendation.getId())
            .stream().map(item -> {
                var persona = item.getBaselinePersona();
                return new PersonaRecommendationResponse.Item(
                    item.getId(), item.getRank(), item.getRecommendationLevel(),
                    item.getFitScore(), item.getConfidence(), item.getMatchReasonsJson(),
                    item.getMismatchRisksJson(), item.getAssumptionsJson(),
                    item.getEvidenceJson(), item.getVerificationQuestionsJson(),
                    item.getInterpretation(),
                    new BaselinePersonaResponse(
                        persona.getId(), persona.getPersonaCode(), persona.getClusterId(),
                        persona.getDisplayName(), persona.getShortName(),
                        persona.getDescription(), persona.getAgeGroup(), persona.getGender(),
                        persona.getSampleSize(), persona.getWeightedShare(),
                        persona.getDataSource(), persona.getDataVersion(),
                        persona.getCatalogVersion(), persona.getKeyTraitsJson(),
                        persona.getDemographicSummaryJson(),
                        persona.getEvidenceMetricsJson(), persona.getLimitationsJson()));
            }).toList();
        var hypothesisResponses = hypotheses
            .findByRecommendationIdAndDeletedAtIsNullOrderById(recommendation.getId())
            .stream().map(item -> new PersonaRecommendationResponse.Hypothesis(
                item.getId(), item.getPersonaCode(), item.getHypothesisType(),
                item.getStatement(), item.getRationale(), item.getSourceType(),
                item.getSourceReference(), item.getConfidence(), item.getPriority(),
                item.getValidationStatus())).toList();
        var validationPlanResponses = validationPlans
            .findByRecommendationIdAndDeletedAtIsNullOrderById(recommendation.getId())
            .stream().map(item -> new PersonaRecommendationResponse.ValidationPlan(
                item.getId(), item.getPersonaCode(), item.getMethod(), item.getObjective(),
                item.getTargetParticipantDescription(), item.getSuggestedSampleSize(),
                item.getRecruitmentChannel(), item.getSuccessCriteriaJson(),
                item.getExpectedEvidenceJson(), item.getInterviewQuestionsJson(),
                item.getSurveyQuestionsJson(), item.getLinkedFeasibilityTaskIdsJson(),
                item.getPriority(), item.getStatus(), item.getDisclaimer())).toList();
        var taskResponses = taskLinks
            .findByRecommendationIdAndDeletedAtIsNullOrderById(recommendation.getId())
            .stream().map(link -> {
                var task = link.getFeasibilityValidationTask();
                return new PersonaRecommendationResponse.LinkedTask(
                    link.getId(), task.getId(), task.getTaskCode(), task.getTitle(),
                    task.getPriority().name(), task.getStatus().name());
            }).toList();
        return new PersonaRecommendationResponse(
            recommendation.getId(), recommendation.getProject().getId(),
            recommendation.getAnalysisJob().getId(),
            recommendation.getStructuredPlan().getId(),
            recommendation.getFeasibilityAssessment().getId(),
            recommendation.getStatus(), recommendation.getPrimaryPersonaCode(),
            recommendation.getSecondaryPersonaCode(), recommendation.getConfidence(),
            recommendation.getSummary(), recommendation.getDisclaimer(),
            recommendation.getProvider(), recommendation.getModelName(),
            recommendation.getPromptVersion(), recommendation.getCatalogVersion(),
            recommendation.getCompletedAt(), itemResponses, hypothesisResponses,
            validationPlanResponses, taskResponses);
    }
}
