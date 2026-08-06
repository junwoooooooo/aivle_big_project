package com.aivle.backend.pipeline.concept.domain;

public final class ConceptFactoryLimits {
    public static final int SLOT_COUNT = 5;
    public static final int MAX_LEGAL_REDESIGNS_PER_SLOT = 1;
    public static final int MAX_REPLACEMENT_ROUNDS = 2;
    public static final int MAX_INSPECTED_CANDIDATES = 15;
    public static final int MAX_PROVIDER_TRANSIENT_RETRIES = 1;

    private ConceptFactoryLimits() {}
}
