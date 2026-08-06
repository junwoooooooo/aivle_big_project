package com.aivle.backend.integration.ai.persona;

import java.util.List;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

public record PersonaRecommendationAiResponse(
    String provider,
    String model,
    String providerRequestId,
    String summary,
    PersonaConfidence confidence,
    List<Item> items,
    List<Hypothesis> hypotheses,
    List<ValidationPlan> validationPlans
) {
    public record Item(
        String personaCode,
        Integer rank,
        Integer fitScore,
        PersonaConfidence confidence,
        List<String> matchReasons,
        List<String> mismatchRisks,
        List<String> assumptions,
        List<Evidence> evidence,
        List<String> verificationQuestions,
        String interpretation
    ) {}
    public record Evidence(
        String sourceType, String description, String reference
    ) {}
    public record Hypothesis(
        String personaCode,
        HypothesisType type,
        String statement,
        String rationale,
        HypothesisSourceType sourceType,
        String sourceReference,
        PersonaConfidence confidence,
        ValidationPriority priority
    ) {}
    public record ValidationPlan(
        String personaCode,
        ValidationMethod method,
        String objective,
        String targetParticipantDescription,
        Integer suggestedSampleSize,
        String recruitmentChannel,
        List<String> successCriteria,
        List<String> expectedEvidence,
        List<InterviewQuestion> interviewQuestions,
        List<SurveyQuestion> surveyQuestions,
        List<Long> linkedFeasibilityTaskIds,
        ValidationPriority priority
    ) {}
    public record InterviewQuestion(
        Integer order,
        String question,
        String purpose,
        String linkedHypothesis,
        Boolean avoidLeading,
        String followUpPrompt,
        String evidenceExpected
    ) {}
    public record SurveyQuestion(
        Integer order,
        SurveyQuestionType questionType,
        String question,
        List<String> options,
        Boolean required,
        String purpose,
        String linkedHypothesis,
        String scaleMinLabel,
        String scaleMaxLabel
    ) {}
}
