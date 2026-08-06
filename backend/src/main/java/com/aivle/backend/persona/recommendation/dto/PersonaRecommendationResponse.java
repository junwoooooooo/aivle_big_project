package com.aivle.backend.persona.recommendation.dto;

import com.aivle.backend.persona.catalog.dto.BaselinePersonaResponse;
import com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;
import java.time.LocalDateTime;
import java.util.List;

public record PersonaRecommendationResponse(
    Long recommendationId,
    Long projectId,
    Long analysisJobId,
    Long structuredPlanId,
    Long feasibilityAssessmentId,
    RecommendationStatus status,
    String primaryPersonaCode,
    String secondaryPersonaCode,
    PersonaConfidence confidence,
    String summary,
    String disclaimer,
    String provider,
    String modelName,
    String promptVersion,
    String catalogVersion,
    LocalDateTime completedAt,
    List<Item> items,
    List<Hypothesis> hypotheses,
    List<ValidationPlan> validationPlans,
    List<LinkedTask> linkedFeasibilityTasks
) {
    public record Item(
        Long id, Integer rank, RecommendationLevel recommendationLevel,
        Integer fitScore, PersonaConfidence confidence, String matchReasonsJson,
        String mismatchRisksJson, String assumptionsJson, String evidenceJson,
        String verificationQuestionsJson, String interpretation,
        BaselinePersonaResponse baselinePersona
    ) {}

    public record Hypothesis(
        Long id, String personaCode, HypothesisType hypothesisType,
        String statement, String rationale, HypothesisSourceType sourceType,
        String sourceReference, PersonaConfidence confidence,
        ValidationPriority priority, HypothesisValidationStatus validationStatus
    ) {}

    public record ValidationPlan(
        Long id, String personaCode, ValidationMethod method, String objective,
        String targetParticipantDescription, Integer suggestedSampleSize,
        String recruitmentChannel, String successCriteriaJson,
        String expectedEvidenceJson, String interviewQuestionsJson,
        String surveyQuestionsJson, String linkedFeasibilityTaskIdsJson,
        ValidationPriority priority, ValidationPlanStatus status, String disclaimer
    ) {}

    public record LinkedTask(
        Long id, Long feasibilityValidationTaskId, String taskCode,
        String title, String priority, String status
    ) {}
}
