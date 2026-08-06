package com.aivle.backend.analysis.feasibility.entity;

public final class FeasibilityTypes {
    private FeasibilityTypes() {}

    public enum DimensionCode {
        PROBLEM_AND_NEED,
        TARGET_CUSTOMER,
        MARKET_ATTRACTIVENESS,
        COMPETITIVE_POSITION,
        PRODUCT_SOLUTION_FIT,
        BUSINESS_MODEL,
        GO_TO_MARKET,
        FINANCIAL_VIABILITY,
        EXECUTION_CAPABILITY,
        LEGAL_AND_REGULATORY
    }

    public enum Confidence { LOW, MEDIUM, HIGH }
    public enum Verdict { PROMISING, CONDITIONAL, HIGH_RISK, INSUFFICIENT_INFORMATION }
    public enum AssessmentStatus { NEEDS_VALIDATION, COMPLETED }
    public enum DimensionStatus { ASSESSED, NEEDS_VALIDATION, INSUFFICIENT_INFORMATION }
    public enum EvidenceType {
        DOCUMENT_FACT, USER_ASSUMPTION, AI_INFERENCE, LEGAL_REVIEW,
        EXTERNAL_VERIFICATION_REQUIRED
    }
    public enum ValidationPriority { HIGH, MEDIUM, LOW }
    public enum ValidationTaskStatus { OPEN, COMPLETED, DISMISSED }
}
