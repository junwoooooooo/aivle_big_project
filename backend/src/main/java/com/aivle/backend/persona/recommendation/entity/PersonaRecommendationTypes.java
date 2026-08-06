package com.aivle.backend.persona.recommendation.entity;

public final class PersonaRecommendationTypes {
    private PersonaRecommendationTypes() {}

    public enum RecommendationStatus { COMPLETED, NEEDS_VALIDATION }
    public enum RecommendationLevel {
        PRIMARY, SECONDARY, LOW_PRIORITY, INSUFFICIENT_INFORMATION
    }
    public enum PersonaConfidence { LOW, MEDIUM, HIGH }
    public enum HypothesisType {
        PROBLEM, CUSTOMER_SEGMENT, VALUE_PROPOSITION, CHANNEL,
        WILLINGNESS_TO_PAY, PURCHASE_BEHAVIOR, SUBSCRIPTION_BEHAVIOR,
        TECHNOLOGY_ADOPTION, RETENTION, LEGAL_CONCERN
    }
    public enum HypothesisSourceType {
        DOCUMENT_FACT, USER_ASSUMPTION, AI_INFERENCE, FEASIBILITY_TASK,
        LEGAL_REVIEW, EXTERNAL_VERIFICATION_REQUIRED
    }
    public enum ValidationPriority { HIGH, MEDIUM, LOW }
    public enum HypothesisValidationStatus { OPEN }
    public enum ValidationMethod {
        INTERVIEW, SURVEY, LANDING_PAGE_TEST, PROTOTYPE_TEST, PRICE_TEST, DESK_RESEARCH
    }
    public enum ValidationPlanStatus { DRAFT }
    public enum SurveyQuestionType {
        SINGLE_CHOICE, MULTIPLE_CHOICE, SCALE, SHORT_TEXT, LONG_TEXT
    }
}
