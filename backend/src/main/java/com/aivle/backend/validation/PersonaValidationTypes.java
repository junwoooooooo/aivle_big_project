package com.aivle.backend.validation;

public final class PersonaValidationTypes {
    private PersonaValidationTypes() { }

    public enum ValidationStatus { DRAFT, COMPLETED, FAILED, ARCHIVED }
    public enum InterviewPurpose {
        PROBLEM_DISCOVERY,
        VALUE_PROPOSITION,
        PURCHASE_MOTIVATION,
        MESSAGE_REACTION,
        CUSTOM
    }
    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE, MIXED }
}
