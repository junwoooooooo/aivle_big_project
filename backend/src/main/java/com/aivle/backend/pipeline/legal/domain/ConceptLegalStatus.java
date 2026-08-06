package com.aivle.backend.pipeline.legal.domain;

public enum ConceptLegalStatus {
    IMPLEMENTABLE,
    IMPLEMENTABLE_WITH_CONTROLS,
    NEEDS_FACTS,
    REDESIGNABLE,
    REJECTED;

    public boolean isPubliclyEligible() {
        return this == IMPLEMENTABLE || this == IMPLEMENTABLE_WITH_CONTROLS;
    }
}
