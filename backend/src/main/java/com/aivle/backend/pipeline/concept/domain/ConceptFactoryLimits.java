package com.aivle.backend.pipeline.concept.domain;

public final class ConceptFactoryLimits {
    public static final int SLOT_COUNT = 5;
    public static final int MAX_LEGAL_REDESIGNS_PER_SLOT = 1;
    public static final int MAX_REPLACEMENT_ROUNDS = 2;
    public static final int MAX_INSPECTED_CANDIDATES = SLOT_COUNT
        * (1 + MAX_REPLACEMENT_ROUNDS + MAX_LEGAL_REDESIGNS_PER_SLOT);
    public static final int MAX_PROVIDER_TRANSIENT_RETRIES_PER_CALL = 2;

    private ConceptFactoryLimits() {}
}
